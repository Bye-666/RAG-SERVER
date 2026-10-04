package com.ragserver.retrieval;

import com.ragserver.ai.dashscope.DashScopeChatClient;
import com.ragserver.retrieval.model.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 重排序服务
 *
 * <p>基于LLM的文档重排序，提升检索结果相关性：</p>
 * <ul>
 *   <li>LLM精排：利用大模型理解query-doc相关性</li>
 *   <li>超时回退：Rerank失败时保持原排序</li>
 *   <li>批量处理：一次性对所有候选文档打分</li>
 *   <li>可解释性：返回相关性分数和理由</li>
 * </ul>
 *
 * <h3>工作原理</h3>
 * <pre>
 * 1. 构建Rerank Prompt：包含query和所有候选文档
 * 2. 调用LLM：要求对每个文档打分（0-10）
 * 3. 解析响应：提取每个文档的分数
 * 4. 重新排序：按分数降序排列，返回topK
 * </pre>
 *
 * <h3>使用建议</h3>
 * <ul>
 *   <li>候选文档数量：10-20个（太多会超token限制）</li>
 *   <li>适用场景：高质量要求的查询（如问答、推荐）</li>
 *   <li>不适用：大批量检索、实时性要求极高的场景</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Service
public class RerankerService {

    private final DashScopeChatClient chatClient;

    /**
     * Rerank超时时间（毫秒）
     */
    private static final int RERANK_TIMEOUT_MS = 10000;

    /**
     * 最大候选文档数量（防止超token）
     */
    private static final int MAX_CANDIDATES = 20;

    public RerankerService(DashScopeChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 重排序文档
     *
     * <p>使用LLM对候选文档进行相关性打分和重排序。</p>
     *
     * @param query 查询文本
     * @param candidates 候选文档列表
     * @param topK 返回前K个文档
     * @return 重排序后的文档列表
     */
    public List<Document> rerank(String query, List<Document> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            log.warn("候选文档为空，无需Rerank");
            return List.of();
        }

        if (candidates.size() <= topK) {
            log.debug("候选文档数({})不超过topK({})，无需Rerank", candidates.size(), topK);
            return candidates;
        }

        log.info("开始Rerank：query={}, candidates={}, topK={}", query, candidates.size(), topK);

        long startTime = System.currentTimeMillis();

        try {
            // 限制候选文档数量
            List<Document> limitedCandidates = candidates.size() > MAX_CANDIDATES
                    ? candidates.subList(0, MAX_CANDIDATES)
                    : candidates;

            // 1. 构建Rerank Prompt
            String prompt = buildRerankPrompt(query, limitedCandidates);

            // 2. 调用LLM
            String response = chatClient.chat(prompt);

            // 3. 解析分数
            Map<Integer, Double> scores = parseScores(response, limitedCandidates.size());

            // 4. 重新排序
            List<Document> rerankedDocs = reorderDocuments(limitedCandidates, scores, topK);

            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("Rerank完成：返回{}个文档，耗时{}ms", rerankedDocs.size(), elapsedTime);

            return rerankedDocs;

        } catch (Exception e) {
            log.error("Rerank失败，回退到原排序：{}", e.getMessage());
            // 超时或失败，返回原排序的topK
            return candidates.subList(0, Math.min(topK, candidates.size()));
        }
    }

    /**
     * 构建Rerank Prompt
     *
     * @param query 查询文本
     * @param candidates 候选文档列表
     * @return Prompt字符串
     */
    private String buildRerankPrompt(String query, List<Document> candidates) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个文档相关性评分专家。请根据用户查询，对以下每个文档的相关性进行打分（0-10分）。\n\n");
        prompt.append("用户查询：").append(query).append("\n\n");
        prompt.append("候选文档：\n");

        for (int i = 0; i < candidates.size(); i++) {
            Document doc = candidates.get(i);
            String text = doc.getText();

            // 截断过长的文档（防止超token）
            if (text.length() > 500) {
                text = text.substring(0, 500) + "...";
            }

            prompt.append(String.format("[文档%d]\n%s\n\n", i + 1, text));
        }

        prompt.append("请按以下格式输出每个文档的分数（一行一个）：\n");
        prompt.append("文档1: 分数\n");
        prompt.append("文档2: 分数\n");
        prompt.append("...\n\n");
        prompt.append("只输出分数，不需要解释。分数范围0-10，10表示最相关。");

        return prompt.toString();
    }

    /**
     * 解析LLM返回的分数
     *
     * @param response LLM响应
     * @param expectedCount 期望的文档数量
     * @return 文档索引 -> 分数的映射
     */
    private Map<Integer, Double> parseScores(String response, int expectedCount) {
        Map<Integer, Double> scores = new HashMap<>();

        // 正则匹配：文档N: 分数
        Pattern pattern = Pattern.compile("文档(\\d+)[：:](\\s*)([0-9.]+)");
        Matcher matcher = pattern.matcher(response);

        while (matcher.find()) {
            try {
                int docIndex = Integer.parseInt(matcher.group(1)) - 1; // 转为0-based索引
                double score = Double.parseDouble(matcher.group(3));

                // 验证分数范围
                if (score < 0) score = 0;
                if (score > 10) score = 10;

                scores.put(docIndex, score);
            } catch (NumberFormatException e) {
                log.warn("解析分数失败：{}", matcher.group());
            }
        }

        log.debug("解析到{}个文档分数（期望{}个）", scores.size(), expectedCount);

        // 如果解析失败，给所有文档默认分数
        if (scores.isEmpty()) {
            log.warn("未能解析任何分数，使用默认分数");
            for (int i = 0; i < expectedCount; i++) {
                scores.put(i, 5.0); // 默认中等分数
            }
        }

        return scores;
    }

    /**
     * 根据分数重新排序文档
     *
     * @param candidates 候选文档
     * @param scores 分数映射
     * @param topK 返回数量
     * @return 重排序后的文档列表
     */
    private List<Document> reorderDocuments(List<Document> candidates, Map<Integer, Double> scores, int topK) {
        // 创建带索引的文档列表
        List<IndexedDocument> indexedDocs = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            double score = scores.getOrDefault(i, 0.0); // 未打分的文档给0分
            indexedDocs.add(new IndexedDocument(i, candidates.get(i), score));
        }

        // 按分数降序排序
        indexedDocs.sort((a, b) -> Double.compare(b.score, a.score));

        // 取topK
        return indexedDocs.stream()
                .limit(topK)
                .map(indexed -> {
                    // 将Rerank分数存入metadata（可选）
                    Document doc = indexed.document;
                    doc.getMetadata().put("rerank_score", indexed.score);
                    return doc;
                })
                .collect(Collectors.toList());
    }

    /**
     * 带索引和分数的文档（内部辅助类）
     */
    private static class IndexedDocument {
        final int index;
        final Document document;
        final double score;

        IndexedDocument(int index, Document document, double score) {
            this.index = index;
            this.document = document;
            this.score = score;
        }
    }
}

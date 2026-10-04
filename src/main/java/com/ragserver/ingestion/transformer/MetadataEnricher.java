package com.ragserver.ingestion.transformer;

import com.ragserver.ai.dashscope.DashScopeChatClient;
import com.ragserver.retrieval.model.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 元数据增强器
 *
 * <p>为文档块添加结构化元数据，包括标题、关键词、标签等。</p>
 *
 * <h3>增强策略</h3>
 * <ol>
 *   <li><b>规则模式</b>：基于正则表达式提取标题、关键词</li>
 *   <li><b>LLM模式</b>（可选）：使用LLM生成摘要和标签</li>
 *   <li><b>降级机制</b>：LLM失败时自动回退到规则模式</li>
 * </ol>
 *
 * <h3>元数据字段</h3>
 * <ul>
 *   <li>title: 文档块标题（从Markdown标题或首句提取）</li>
 *   <li>keywords: 关键词列表（从文本中提取高频词）</li>
 *   <li>tags: 分类标签（规则提取或LLM生成）</li>
 *   <li>summary: 摘要（可选，仅LLM模式）</li>
 *   <li>chunk_index: 块在原文档中的位置</li>
 *   <li>char_count: 字符数统计</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * MetadataEnricher enricher = new MetadataEnricher(chatClient);
 * enricher.setUseLlm(false); // 仅使用规则模式
 *
 * Document chunk = Document.builder()
 *     .id("chunk_1")
 *     .text("## 向量数据库\n\nMilvus是一个开源向量数据库...")
 *     .metadata(Map.of("source_path", "/docs/milvus.pdf"))
 *     .build();
 *
 * Document enriched = enricher.enrich(chunk, 0);
 * System.out.println("标题: " + enriched.getMetadata().get("title"));
 * System.out.println("关键词: " + enriched.getMetadata().get("keywords"));
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Component
public class MetadataEnricher {

    /**
     * Markdown标题模式（# 标题）
     */
    private static final Pattern MARKDOWN_TITLE_PATTERN = Pattern.compile("^#+\\s+(.+)$", Pattern.MULTILINE);

    /**
     * 中文停用词列表
     */
    private static final Set<String> STOP_WORDS = Set.of(
        "的", "了", "和", "是", "在", "有", "个", "与", "等", "中", "为", "对", "及",
        "于", "或", "而", "也", "以", "可", "将", "并", "从", "到", "由", "被", "把"
    );

    /**
     * 英文停用词列表
     */
    private static final Set<String> ENGLISH_STOP_WORDS = Set.of(
        "the", "is", "at", "which", "on", "a", "an", "as", "are", "was", "were",
        "been", "be", "have", "has", "had", "do", "does", "did", "will", "would",
        "should", "could", "may", "might", "can", "of", "for", "to", "in", "with"
    );

    private final DashScopeChatClient chatClient;

    /**
     * 是否启用LLM模式生成元数据
     */
    private boolean useLlm = false;

    /**
     * 每个chunk提取的关键词数量
     */
    private int maxKeywords = 5;

    /**
     * 构造函数
     *
     * @param chatClient DashScope聊天客户端（可选，用于LLM模式）
     */
    public MetadataEnricher(DashScopeChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 增强文档块的元数据
     *
     * @param chunk      待增强的文档块
     * @param chunkIndex 块在原文档中的索引位置
     * @return 增强后的文档块
     */
    public Document enrich(Document chunk, int chunkIndex) {
        log.debug("开始增强文档块元数据: id={}, chunkIndex={}", chunk.getId(), chunkIndex);

        // 复制现有元数据
        Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());

        // 1. 提取标题
        String title = extractTitle(chunk.getText());
        metadata.put("title", title);

        // 2. 提取关键词
        List<String> keywords = extractKeywords(chunk.getText());
        metadata.put("keywords", keywords);

        // 3. 添加基础统计信息
        metadata.put("chunk_index", chunkIndex);
        metadata.put("char_count", chunk.getText().length());

        // 4. 尝试使用LLM生成标签和摘要（如果启用且可用）
        if (useLlm && chatClient != null) {
            try {
                enrichWithLlm(chunk.getText(), metadata);
            } catch (Exception e) {
                log.warn("LLM增强失败，回退到规则模式: {}", e.getMessage());
                // 降级到规则模式生成标签
                List<String> tags = extractTagsFromRules(chunk.getText());
                metadata.put("tags", tags);
            }
        } else {
            // 规则模式生成标签
            List<String> tags = extractTagsFromRules(chunk.getText());
            metadata.put("tags", tags);
        }

        log.debug("元数据增强完成: title={}, keywords={}, tags={}",
            title, keywords, metadata.get("tags"));

        return Document.builder()
            .id(chunk.getId())
            .text(chunk.getText())
            .metadata(metadata)
            .build();
    }

    /**
     * 批量增强文档块元数据
     *
     * @param chunks 待增强的文档块列表
     * @return 增强后的文档块列表
     */
    public List<Document> enrichBatch(List<Document> chunks) {
        log.info("批量增强元数据，共{}个块", chunks.size());

        List<Document> enriched = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            enriched.add(enrich(chunks.get(i), i));
        }

        return enriched;
    }

    /**
     * 提取文档标题
     *
     * <p>优先级：</p>
     * <ol>
     *   <li>Markdown标题（# 开头）</li>
     *   <li>第一行文本（限制50字符）</li>
     *   <li>前20个字符</li>
     * </ol>
     *
     * @param text 文档文本
     * @return 提取的标题
     */
    private String extractTitle(String text) {
        if (text == null || text.isEmpty()) {
            return "无标题";
        }

        // 1. 尝试提取Markdown标题
        Matcher matcher = MARKDOWN_TITLE_PATTERN.matcher(text);
        if (matcher.find()) {
            String title = matcher.group(1).trim();
            return title.length() > 50 ? title.substring(0, 50) + "..." : title;
        }

        // 2. 使用第一行作为标题
        String firstLine = text.split("\n")[0].trim();
        if (!firstLine.isEmpty()) {
            return firstLine.length() > 50 ? firstLine.substring(0, 50) + "..." : firstLine;
        }

        // 3. 使用前20个字符
        return text.length() > 20 ? text.substring(0, 20) + "..." : text;
    }

    /**
     * 提取关键词
     *
     * <p>算法：</p>
     * <ol>
     *   <li>分词（按空格、标点分割）</li>
     *   <li>过滤停用词和短词</li>
     *   <li>统计词频</li>
     *   <li>返回Top K高频词</li>
     * </ol>
     *
     * @param text 文档文本
     * @return 关键词列表
     */
    private List<String> extractKeywords(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 分词（简单按空格和标点分割）
        String[] tokens = text.toLowerCase()
            .replaceAll("[^\\p{L}\\p{N}\\s]", " ") // 保留字母、数字、空格
            .split("\\s+");

        // 2. 统计词频（过滤停用词和短词）
        Map<String, Integer> wordFreq = new HashMap<>();
        for (String token : tokens) {
            // 跳过停用词和短词
            if (token.length() < 2 || STOP_WORDS.contains(token) || ENGLISH_STOP_WORDS.contains(token)) {
                continue;
            }

            wordFreq.put(token, wordFreq.getOrDefault(token, 0) + 1);
        }

        // 3. 按词频排序，返回Top K
        return wordFreq.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(maxKeywords)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /**
     * 基于规则提取标签
     *
     * <p>规则：</p>
     * <ul>
     *   <li>包含"数据库"、"向量"、"检索" → 标签：向量数据库</li>
     *   <li>包含"Spring"、"Java"、"API" → 标签：后端开发</li>
     *   <li>包含"PDF"、"文档"、"摄取" → 标签：文档处理</li>
     *   <li>包含"Embedding"、"模型"、"LLM" → 标签：AI模型</li>
     * </ul>
     *
     * @param text 文档文本
     * @return 标签列表
     */
    private List<String> extractTagsFromRules(String text) {
        List<String> tags = new ArrayList<>();
        String lowerText = text.toLowerCase();

        // 规则匹配
        if (containsAny(lowerText, "数据库", "向量", "检索", "milvus", "vector", "database")) {
            tags.add("向量数据库");
        }
        if (containsAny(lowerText, "spring", "java", "api", "rest", "controller")) {
            tags.add("后端开发");
        }
        if (containsAny(lowerText, "pdf", "文档", "摄取", "document", "ingestion")) {
            tags.add("文档处理");
        }
        if (containsAny(lowerText, "embedding", "模型", "llm", "ai", "机器学习")) {
            tags.add("AI模型");
        }
        if (containsAny(lowerText, "测试", "单元测试", "test", "junit")) {
            tags.add("测试");
        }
        if (containsAny(lowerText, "配置", "properties", "yaml", "config")) {
            tags.add("配置管理");
        }

        // 如果没有匹配到任何标签，添加默认标签
        if (tags.isEmpty()) {
            tags.add("其他");
        }

        return tags;
    }

    /**
     * 使用LLM增强元数据
     *
     * <p>生成：</p>
     * <ul>
     *   <li>tags: 分类标签（3-5个）</li>
     *   <li>summary: 一句话摘要（可选）</li>
     * </ul>
     *
     * @param text     文档文本
     * @param metadata 元数据Map（会被直接修改）
     */
    private void enrichWithLlm(String text, Map<String, Object> metadata) {
        // 构建prompt
        String prompt = String.format("""
            请分析以下文本，提取关键信息：

            文本：
            %s

            请以JSON格式返回：
            {
              "tags": ["标签1", "标签2", "标签3"],
              "summary": "一句话摘要"
            }

            要求：
            1. tags包含3-5个分类标签
            2. summary不超过50字
            3. 只返回JSON，不要其他内容
            """, text.length() > 500 ? text.substring(0, 500) + "..." : text);

        // 调用LLM
        String response = chatClient.chat(prompt);

        // 解析响应（简单字符串匹配）
        List<String> tags = parseTagsFromResponse(response);
        String summary = parseSummaryFromResponse(response);

        if (!tags.isEmpty()) {
            metadata.put("tags", tags);
        }
        if (summary != null && !summary.isEmpty()) {
            metadata.put("summary", summary);
        }
    }

    /**
     * 从LLM响应中解析标签
     *
     * @param response LLM响应文本
     * @return 标签列表
     */
    private List<String> parseTagsFromResponse(String response) {
        // 简单正则匹配 "tags": ["tag1", "tag2"]
        Pattern pattern = Pattern.compile("\"tags\"\\s*:\\s*\\[([^\\]]+)\\]");
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            String tagsStr = matcher.group(1);
            return Arrays.stream(tagsStr.split(","))
                .map(s -> s.replaceAll("\"", "").trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    /**
     * 从LLM响应中解析摘要
     *
     * @param response LLM响应文本
     * @return 摘要文本
     */
    private String parseSummaryFromResponse(String response) {
        // 简单正则匹配 "summary": "摘要内容"
        Pattern pattern = Pattern.compile("\"summary\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return null;
    }

    /**
     * 检查文本是否包含任一关键词
     *
     * @param text     待检查文本
     * @param keywords 关键词列表
     * @return 是否包含
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    // ==================== Getters & Setters ====================

    public void setUseLlm(boolean useLlm) {
        this.useLlm = useLlm;
    }

    public void setMaxKeywords(int maxKeywords) {
        this.maxKeywords = maxKeywords;
    }
}

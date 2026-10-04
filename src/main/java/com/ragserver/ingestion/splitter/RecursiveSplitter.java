package com.ragserver.ingestion.splitter;

import com.ragserver.retrieval.model.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 递归文档分块器
 *
 * <p>按Markdown结构递归切分文档，保持语义完整性。</p>
 *
 * <h3>切分策略</h3>
 * <ol>
 *   <li>优先按Markdown标题分割（##、###）</li>
 *   <li>其次按段落分割（双换行）</li>
 *   <li>最后按句子分割（句号、问号、感叹号）</li>
 *   <li>如果单句超长，按字符硬切分</li>
 * </ol>
 *
 * <h3>配置参数</h3>
 * <ul>
 *   <li>chunkSize: 目标块大小（默认512字符）</li>
 *   <li>chunkOverlap: 块之间重叠字符数（默认128字符）</li>
 *   <li>separators: 分隔符优先级列表</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * RecursiveSplitter splitter = new RecursiveSplitter();
 * splitter.setChunkSize(512);
 * splitter.setChunkOverlap(128);
 *
 * Document longDoc = Document.builder()
 *     .id("doc_1")
 *     .text("很长的文档内容...")
 *     .build();
 *
 * List<Document> chunks = splitter.split(longDoc);
 * System.out.println("切分为" + chunks.size() + "个块");
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Component
public class RecursiveSplitter {

    /**
     * 目标块大小（字符数）
     */
    private int chunkSize = 512;

    /**
     * 块之间重叠字符数
     */
    private int chunkOverlap = 128;

    /**
     * 分隔符优先级列表（从高到低）
     */
    private static final String[] SEPARATORS = {
        "\n## ",      // Markdown二级标题
        "\n### ",     // Markdown三级标题
        "\n#### ",    // Markdown四级标题
        "\n\n",       // 段落
        "\n",         // 行
        "。",         // 中文句号
        "！",         // 中文感叹号
        "？",         // 中文问号
        ". ",         // 英文句号
        "! ",         // 英文感叹号
        "? ",         // 英文问号
        "；",         // 中文分号
        "; ",         // 英文分号
        "，",         // 中文逗号
        ", ",         // 英文逗号
        " ",          // 空格
        ""            // 最后按字符切分
    };

    /**
     * 切分单个文档
     *
     * @param document 待切分的文档
     * @return 切分后的文档块列表
     */
    public List<Document> split(Document document) {
        if (document == null || document.getText() == null || document.getText().isEmpty()) {
            log.warn("文档为空，跳过切分");
            return Collections.emptyList();
        }

        String text = document.getText();
        log.debug("开始切分文档: id={}, 文本长度={}", document.getId(), text.length());

        // 如果文本长度小于等于chunk_size，直接返回
        if (text.length() <= chunkSize) {
            log.debug("文本长度 <= chunkSize，无需切分");
            return Collections.singletonList(document);
        }

        // 递归切分
        List<String> chunks = recursiveSplit(text, SEPARATORS, 0);

        // 创建Document对象
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);

            // 继承元数据并添加chunk信息
            Map<String, Object> metadata = new HashMap<>();
            if (document.getMetadata() != null) {
                metadata.putAll(document.getMetadata());
            }
            metadata.put("chunk_index", i);
            metadata.put("chunk_total", chunks.size());
            metadata.put("original_doc_id", document.getId());

            Document chunkDoc = Document.builder()
                .id(document.getId() + "_chunk_" + i)
                .text(chunkText)
                .metadata(metadata)
                .build();

            documents.add(chunkDoc);
        }

        log.info("文档切分完成: {} → {}个块", document.getId(), documents.size());

        return documents;
    }

    /**
     * 批量切分文档
     *
     * @param documents 待切分的文档列表
     * @return 切分后的文档块列表
     */
    public List<Document> splitBatch(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("批量切分文档: {}个", documents.size());
        List<Document> allChunks = new ArrayList<>();

        for (Document doc : documents) {
            List<Document> chunks = split(doc);
            allChunks.addAll(chunks);
        }

        log.info("批量切分完成: {}个文档 → {}个块", documents.size(), allChunks.size());

        return allChunks;
    }

    /**
     * 递归切分文本
     *
     * @param text 待切分文本
     * @param separators 分隔符列表
     * @param separatorIndex 当前分隔符索引
     * @return 切分后的文本块列表
     */
    private List<String> recursiveSplit(String text, String[] separators, int separatorIndex) {
        List<String> result = new ArrayList<>();

        // 如果文本长度小于等于chunk_size，直接返回
        if (text.length() <= chunkSize) {
            result.add(text);
            return result;
        }

        // 如果已经到最后一个分隔符（空字符串），按字符硬切分
        if (separatorIndex >= separators.length) {
            return hardSplit(text);
        }

        String separator = separators[separatorIndex];

        // 空分隔符表示按字符切分
        if (separator.isEmpty()) {
            return hardSplit(text);
        }

        // 按当前分隔符切分
        String[] parts = text.split(Pattern.quote(separator), -1);

        // 如果切分失败（只有一个部分），尝试下一个分隔符
        if (parts.length == 1) {
            return recursiveSplit(text, separators, separatorIndex + 1);
        }

        // 合并小块，保持overlap
        List<String> mergedParts = mergeParts(parts, separator);

        // 对每个部分递归切分
        for (String part : mergedParts) {
            if (part.length() > chunkSize) {
                // 继续用下一个分隔符切分
                result.addAll(recursiveSplit(part, separators, separatorIndex + 1));
            } else {
                result.add(part);
            }
        }

        return result;
    }

    /**
     * 合并小块，添加overlap
     *
     * @param parts 切分后的部分
     * @param separator 分隔符
     * @return 合并后的块列表
     */
    private List<String> mergeParts(String[] parts, String separator) {
        List<String> result = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        String previousChunk = "";

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];

            // 恢复分隔符（除了最后一个部分）
            if (i < parts.length - 1) {
                part = part + separator;
            }

            // 如果当前块加上新部分超过chunk_size
            if (currentChunk.length() > 0 && currentChunk.length() + part.length() > chunkSize) {
                // 保存当前块
                result.add(currentChunk.toString());
                previousChunk = currentChunk.toString();

                // 开始新块，添加overlap
                currentChunk = new StringBuilder();
                if (chunkOverlap > 0 && previousChunk.length() > 0) {
                    // 从上一个块末尾取overlap字符
                    int overlapStart = Math.max(0, previousChunk.length() - chunkOverlap);
                    String overlap = previousChunk.substring(overlapStart);
                    currentChunk.append(overlap);
                }
            }

            currentChunk.append(part);
        }

        // 添加最后一个块
        if (currentChunk.length() > 0) {
            result.add(currentChunk.toString());
        }

        return result;
    }

    /**
     * 硬切分（按字符）
     *
     * @param text 待切分文本
     * @return 切分后的块列表
     */
    private List<String> hardSplit(String text) {
        List<String> result = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunk = text.substring(start, end);
            result.add(chunk);

            // 移动到下一个位置，减去overlap
            start = end - chunkOverlap;
            if (start >= text.length()) {
                break;
            }
        }

        return result;
    }

    // Getter and Setter

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize必须大于0");
        }
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        if (chunkOverlap < 0) {
            throw new IllegalArgumentException("chunkOverlap不能为负数");
        }
        if (chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("chunkOverlap必须小于chunkSize");
        }
        this.chunkOverlap = chunkOverlap;
    }
}

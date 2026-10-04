package com.ragserver.ingestion.transformer;

import com.ragserver.retrieval.model.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档块精炼器
 *
 * <p>去噪和格式清理，提升文档块的可读性。</p>
 *
 * <h3>清理规则</h3>
 * <ul>
 *   <li>去除多余空白（连续空格、多余换行）</li>
 *   <li>清理页眉页脚（页码、版权信息）</li>
 *   <li>规范化标点符号</li>
 *   <li>保护代码块格式</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * ChunkRefiner refiner = new ChunkRefiner();
 *
 * Document noisyDoc = Document.builder()
 *     .id("doc_1")
 *     .text("   多余空白   \n\n\n 第1页 \n 正文内容...")
 *     .build();
 *
 * Document cleanDoc = refiner.refine(noisyDoc);
 * System.out.println("清理后: " + cleanDoc.getText());
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Component
public class ChunkRefiner {

    /**
     * 页眉页脚模式（页码、版权等）
     */
    private static final Pattern[] HEADER_FOOTER_PATTERNS = {
        Pattern.compile("^第\\s*\\d+\\s*页.*$", Pattern.MULTILINE),           // 第X页
        Pattern.compile("^Page\\s+\\d+.*$", Pattern.MULTILINE),              // Page X
        Pattern.compile("^\\d+\\s*/\\s*\\d+$", Pattern.MULTILINE),          // X/Y 页码
        Pattern.compile("^©.*$", Pattern.MULTILINE),                         // 版权符号
        Pattern.compile("^Copyright.*$", Pattern.MULTILINE),                 // Copyright
        Pattern.compile("^\\[\\s*\\d+\\s*\\]\\s*$", Pattern.MULTILINE),     // [页码]
        Pattern.compile("^-+\\s*\\d+\\s*-+$", Pattern.MULTILINE),           // ---页码---
    };

    /**
     * 代码块标记
     */
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
        "```[\\s\\S]*?```|`[^`]+`",
        Pattern.MULTILINE
    );

    /**
     * 精炼单个文档块
     *
     * @param document 待精炼的文档块
     * @return 精炼后的文档块
     */
    public Document refine(Document document) {
        if (document == null || document.getText() == null || document.getText().isEmpty()) {
            log.warn("文档为空，跳过精炼");
            return document;
        }

        String text = document.getText();
        log.debug("开始精炼文档: id={}, 原始长度={}", document.getId(), text.length());

        // 1. 提取并保护代码块
        Map<String, String> codeBlocks = extractCodeBlocks(text);
        text = replaceCodeBlocksWithPlaceholders(text, codeBlocks);

        // 2. 清理页眉页脚
        text = removeHeadersFooters(text);

        // 3. 规范化空白
        text = normalizeWhitespace(text);

        // 4. 清理特殊字符
        text = cleanSpecialCharacters(text);

        // 5. 恢复代码块
        text = restoreCodeBlocks(text, codeBlocks);

        // 6. 最后修剪
        text = text.trim();

        log.debug("精炼完成: 原始长度={} → 清理后长度={}", document.getText().length(), text.length());

        // 创建新文档（保留元数据）
        return Document.builder()
            .id(document.getId())
            .text(text)
            .metadata(document.getMetadata())
            .denseVector(document.getDenseVector())
            .sparseVector(document.getSparseVector())
            .score(document.getScore())
            .build();
    }

    /**
     * 批量精炼文档块
     *
     * @param documents 待精炼的文档块列表
     * @return 精炼后的文档块列表
     */
    public List<Document> refineBatch(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("批量精炼文档: {}个", documents.size());
        List<Document> refined = new ArrayList<>();

        for (Document doc : documents) {
            refined.add(refine(doc));
        }

        log.info("批量精炼完成: {}个文档", refined.size());

        return refined;
    }

    /**
     * 提取代码块
     *
     * @param text 文本
     * @return 代码块映射（placeholder → code）
     */
    private Map<String, String> extractCodeBlocks(String text) {
        Map<String, String> codeBlocks = new HashMap<>();
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);

        int index = 0;
        while (matcher.find()) {
            String placeholder = "___CODE_BLOCK_" + index + "___";
            String code = matcher.group();
            codeBlocks.put(placeholder, code);
            index++;
        }

        return codeBlocks;
    }

    /**
     * 将代码块替换为占位符
     *
     * @param text 文本
     * @param codeBlocks 代码块映射
     * @return 替换后的文本
     */
    private String replaceCodeBlocksWithPlaceholders(String text, Map<String, String> codeBlocks) {
        String result = text;
        for (Map.Entry<String, String> entry : codeBlocks.entrySet()) {
            result = result.replace(entry.getValue(), entry.getKey());
        }
        return result;
    }

    /**
     * 恢复代码块
     *
     * @param text 文本
     * @param codeBlocks 代码块映射
     * @return 恢复后的文本
     */
    private String restoreCodeBlocks(String text, Map<String, String> codeBlocks) {
        String result = text;
        for (Map.Entry<String, String> entry : codeBlocks.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * 移除页眉页脚
     *
     * @param text 文本
     * @return 清理后的文本
     */
    private String removeHeadersFooters(String text) {
        String result = text;

        for (Pattern pattern : HEADER_FOOTER_PATTERNS) {
            result = pattern.matcher(result).replaceAll("");
        }

        return result;
    }

    /**
     * 规范化空白
     *
     * @param text 文本
     * @return 规范化后的文本
     */
    private String normalizeWhitespace(String text) {
        // 1. 统一换行符
        text = text.replace("\r\n", "\n").replace("\r", "\n");

        // 2. 去除每行首尾空白
        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                result.append(trimmed).append("\n");
            }
        }
        text = result.toString();

        // 3. 合并连续空行（最多保留1个空行）
        text = text.replaceAll("\n{3,}", "\n\n");

        // 4. 合并连续空格
        text = text.replaceAll(" {2,}", " ");

        // 5. 去除制表符
        text = text.replace("\t", " ");

        return text;
    }

    /**
     * 清理特殊字符
     *
     * @param text 文本
     * @return 清理后的文本
     */
    private String cleanSpecialCharacters(String text) {
        // 1. 去除零宽字符（使用Unicode转义）
        text = text.replaceAll("[​-‍﻿]", "");

        // 2. 规范化引号（使用Unicode转义）
        text = text.replace("“", "\"").replace("”", "\"");  // 中文双引号
        text = text.replace("‘", "'").replace("’", "'");    // 中文单引号

        // 3. 规范化破折号
        text = text.replace("—", "-").replace("–", "-");    // em dash, en dash

        // 4. 去除控制字符（保留换行）
        text = text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");

        return text;
    }

    /**
     * 检查文本清理效果
     *
     * @param original 原始文本
     * @param refined 清理后文本
     * @return 清理统计信息
     */
    public Map<String, Object> getCleaningStats(String original, String refined) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("original_length", original.length());
        stats.put("refined_length", refined.length());
        stats.put("removed_chars", original.length() - refined.length());
        stats.put("reduction_rate", String.format("%.2f%%",
            (original.length() - refined.length()) * 100.0 / original.length()));

        // 统计行数变化
        int originalLines = original.split("\n").length;
        int refinedLines = refined.split("\n").length;
        stats.put("original_lines", originalLines);
        stats.put("refined_lines", refinedLines);
        stats.put("removed_lines", originalLines - refinedLines);

        return stats;
    }
}

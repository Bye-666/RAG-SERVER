package com.ragserver.service;

import com.ragserver.retrieval.model.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Prompt服务
 *
 * <p>负责管理和渲染Prompt模板：</p>
 * <ul>
 *   <li>RAG查询Prompt构建</li>
 *   <li>文档引用（Citation）生成</li>
 *   <li>模板加载与缓存</li>
 *   <li>变量替换与渲染</li>
 * </ul>
 *
 * <h3>模板语法</h3>
 * <pre>
 * 变量：{{variable}}
 * 循环：{{#list}} ... {{/list}}
 * 条件：{{#if}} ... {{/if}}
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * List<Document> docs = retriever.retrieve("什么是RAG？", 5);
 * String prompt = promptService.buildRagPrompt("什么是RAG？", docs);
 * String citations = promptService.buildCitations(docs);
 * }</pre>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Slf4j
@Service
public class PromptService {

    /**
     * 模板缓存
     */
    private final Map<String, String> templateCache = new HashMap<>();

    /**
     * RAG查询模板路径
     */
    private static final String RAG_QUERY_TEMPLATE = "prompts/rag-query.st";

    /**
     * 构建RAG查询Prompt
     *
     * <p>将查询问题和检索到的文档上下文渲染到模板中。</p>
     *
     * @param query 用户查询
     * @param context 检索到的文档列表
     * @return 渲染后的Prompt字符串
     */
    public String buildRagPrompt(String query, List<Document> context) {
        log.debug("构建RAG Prompt：query={}, context.size={}", query, context.size());

        // 加载模板
        String template = loadTemplate(RAG_QUERY_TEMPLATE);

        // 准备变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("query", query);
        variables.put("context", buildContextList(context));

        // 渲染模板
        String prompt = renderTemplate(template, variables);

        log.debug("Prompt构建完成，长度：{}", prompt.length());
        return prompt;
    }

    /**
     * 构建文档引用（Citations）
     *
     * <p>生成格式化的来源引用列表。</p>
     *
     * <h3>输出格式</h3>
     * <pre>
     * 引用来源:
     * [1] document.pdf, 第5页
     * [2] guide.md, 第2节
     * </pre>
     *
     * @param documents 文档列表
     * @return 格式化的引用字符串
     */
    public String buildCitations(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "";
        }

        StringBuilder citations = new StringBuilder("引用来源:\n");

        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            String citation = buildSingleCitation(i + 1, doc);
            citations.append(citation).append("\n");
        }

        return citations.toString().trim();
    }

    /**
     * 构建单个文档引用
     *
     * @param index 引用序号（从1开始）
     * @param doc 文档对象
     * @return 格式化的单条引用
     */
    private String buildSingleCitation(int index, Document doc) {
        StringBuilder citation = new StringBuilder();
        citation.append("[").append(index).append("] ");

        // 提取文档来源信息
        String sourcePath = extractSourcePath(doc);
        citation.append(sourcePath);

        // 添加页码信息（如果有）
        Object pageObj = doc.getMetadata().get("page");
        if (pageObj != null) {
            citation.append(", 第").append(pageObj).append("页");
        }

        // 添加章节信息（如果有）
        Object sectionObj = doc.getMetadata().get("section");
        if (sectionObj != null) {
            citation.append(", 第").append(sectionObj).append("节");
        }

        return citation.toString();
    }

    /**
     * 提取文档来源路径
     *
     * @param doc 文档对象
     * @return 文档来源路径或文件名
     */
    private String extractSourcePath(Document doc) {
        // 优先使用metadata中的source_path
        Object sourcePathObj = doc.getMetadata().get("source_path");
        if (sourcePathObj != null) {
            String fullPath = sourcePathObj.toString();
            // 只返回文件名，去掉完整路径
            int lastSlash = Math.max(fullPath.lastIndexOf('/'), fullPath.lastIndexOf('\\'));
            return lastSlash >= 0 ? fullPath.substring(lastSlash + 1) : fullPath;
        }

        // 尝试file_path
        Object filePathObj = doc.getMetadata().get("file_path");
        if (filePathObj != null) {
            String fullPath = filePathObj.toString();
            int lastSlash = Math.max(fullPath.lastIndexOf('/'), fullPath.lastIndexOf('\\'));
            return lastSlash >= 0 ? fullPath.substring(lastSlash + 1) : fullPath;
        }

        // 最后使用文档ID
        return doc.getId() != null ? doc.getId() : "未知来源";
    }

    /**
     * 构建上下文列表（用于模板渲染）
     *
     * @param documents 文档列表
     * @return 模板变量格式的上下文列表
     */
    private List<Map<String, String>> buildContextList(List<Document> documents) {
        return documents.stream()
                .map(doc -> {
                    Map<String, String> contextItem = new HashMap<>();
                    contextItem.put("source_path", extractSourcePath(doc));
                    contextItem.put("content", doc.getText());
                    return contextItem;
                })
                .collect(Collectors.toList());
    }

    /**
     * 加载模板文件
     *
     * <p>首次加载后会缓存到内存中。</p>
     *
     * @param templatePath 模板路径（相对于resources目录）
     * @return 模板内容
     */
    private String loadTemplate(String templatePath) {
        // 检查缓存
        if (templateCache.containsKey(templatePath)) {
            return templateCache.get(templatePath);
        }

        try {
            ClassPathResource resource = new ClassPathResource(templatePath);
            String template = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // 缓存模板
            templateCache.put(templatePath, template);

            log.info("模板加载成功：{}", templatePath);
            return template;

        } catch (IOException e) {
            log.error("模板加载失败：{}", templatePath, e);
            throw new RuntimeException("无法加载Prompt模板：" + templatePath, e);
        }
    }

    /**
     * 渲染模板（简化版Mustache实现）
     *
     * <p>支持以下语法：</p>
     * <ul>
     *   <li>变量替换：{{variable}}</li>
     *   <li>列表循环：{{#list}} ... {{/list}}</li>
     * </ul>
     *
     * @param template 模板字符串
     * @param variables 变量映射
     * @return 渲染后的字符串
     */
    private String renderTemplate(String template, Map<String, Object> variables) {
        String result = template;

        // 处理循环（{{#context}} ... {{/context}}）
        result = renderLoop(result, variables);

        // 处理简单变量替换（{{query}}）
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }

        return result;
    }

    /**
     * 渲染循环语法
     *
     * @param template 模板字符串
     * @param variables 变量映射
     * @return 渲染后的字符串
     */
    private String renderLoop(String template, Map<String, Object> variables) {
        String result = template;

        // 匹配 {{#key}} ... {{/key}}
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            String startTag = "{{#" + key + "}}";
            String endTag = "{{/" + key + "}}";

            int startIndex = result.indexOf(startTag);
            if (startIndex == -1) {
                continue;
            }

            int endIndex = result.indexOf(endTag);
            if (endIndex == -1) {
                log.warn("模板语法错误：找不到结束标签 {}", endTag);
                continue;
            }

            // 提取循环体
            String loopBody = result.substring(startIndex + startTag.length(), endIndex);

            // 渲染循环
            StringBuilder rendered = new StringBuilder();
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                for (Object item : list) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> itemMap = (Map<String, String>) item;
                        String itemRendered = loopBody;
                        for (Map.Entry<String, String> itemEntry : itemMap.entrySet()) {
                            String itemPlaceholder = "{{" + itemEntry.getKey() + "}}";
                            itemRendered = itemRendered.replace(itemPlaceholder, itemEntry.getValue());
                        }
                        rendered.append(itemRendered);
                    }
                }
            }

            // 替换整个循环块
            result = result.substring(0, startIndex) + rendered + result.substring(endIndex + endTag.length());
        }

        return result;
    }

    /**
     * 清除模板缓存
     *
     * <p>开发环境下可用于热更新模板。</p>
     */
    public void clearTemplateCache() {
        templateCache.clear();
        log.info("模板缓存已清除");
    }
}

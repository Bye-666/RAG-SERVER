package com.ragserver.controller;

import com.ragserver.service.DocumentService;
import com.ragserver.service.IngestionService;
import com.ragserver.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RagController 集成测试
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@WebMvcTest(RagController.class)
class RagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngestionService ingestionService;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private RagService ragService;

    /**
     * 测试：摄取文档
     */
    @Test
    void testIngest() throws Exception {
        // Given: Mock文件上传
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "test content".getBytes()
        );

        // When & Then: 上传文件
        mockMvc.perform(multipart("/api/ingest").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("test.pdf"))
                .andExpect(jsonPath("$.status").exists());
    }

    /**
     * 测试：列出所有文档
     */
    @Test
    void testListDocuments() throws Exception {
        // Given: Mock文档列表
        when(documentService.listDocuments()).thenReturn(List.of());

        // When & Then: 调用API
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").isArray())
                .andExpect(jsonPath("$.total").value(0));

        verify(documentService).listDocuments();
    }

    /**
     * 测试：获取文档详情
     */
    @Test
    void testGetDocument() throws Exception {
        // Given: Mock文档详情
        DocumentService.DocumentDetail detail = new DocumentService.DocumentDetail();
        detail.setFileHash("test_hash");
        detail.setFilePath("/test/doc.pdf");
        detail.setFileSize(1024L);
        detail.setStatus("SUCCESS");
        detail.setChunkCount(10);
        detail.setProcessedAt(java.time.Instant.now());
        detail.setErrorMsg(null);

        when(documentService.getDocumentDetail("test_hash")).thenReturn(detail);

        // When & Then: 调用API
        mockMvc.perform(get("/api/documents/test_hash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileHash").value("test_hash"))
                .andExpect(jsonPath("$.filePath").value("/test/doc.pdf"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(documentService).getDocumentDetail("test_hash");
    }

    /**
     * 测试：获取不存在的文档
     */
    @Test
    void testGetDocumentNotFound() throws Exception {
        // Given: 文档不存在
        when(documentService.getDocumentDetail("nonexistent"))
                .thenThrow(new RuntimeException("文档不存在"));

        // When & Then: 返回404
        mockMvc.perform(get("/api/documents/nonexistent"))
                .andExpect(status().isNotFound());
    }

    /**
     * 测试：删除文档
     */
    @Test
    void testDeleteDocument() throws Exception {
        // Given: Mock删除操作
        doNothing().when(documentService).deleteDocument("test_doc_id");

        // When & Then: 调用API
        mockMvc.perform(delete("/api/documents/test_doc_id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.documentId").value("test_doc_id"));

        verify(documentService).deleteDocument("test_doc_id");
    }

    /**
     * 测试：RAG查询
     */
    @Test
    void testQuery() throws Exception {
        // Given: Mock查询结果
        String question = "什么是RAG？";
        String answer = "RAG是检索增强生成技术。";

        when(ragService.query(eq(question), eq(5), eq(false))).thenReturn(answer);

        // When & Then: 调用API
        mockMvc.perform(post("/api/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + question + "\",\"topK\":5,\"enableRerank\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(answer))
                .andExpect(jsonPath("$.question").value(question));

        verify(ragService).query(question, 5, false);
    }

    /**
     * 测试：列出Collection
     */
    @Test
    void testListCollections() throws Exception {
        // Given: Mock统计数据
        DocumentService.CollectionStats stats = new DocumentService.CollectionStats();
        stats.setTotalDocuments(50L);
        stats.setVectorCount(800L);

        when(documentService.getCollectionStats()).thenReturn(stats);

        // When & Then: 调用API
        mockMvc.perform(get("/api/collections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collections").isArray())
                .andExpect(jsonPath("$.collections[0].name").exists())
                .andExpect(jsonPath("$.collections[0].documentCount").value(50))
                .andExpect(jsonPath("$.collections[0].vectorCount").value(800));

        verify(documentService).getCollectionStats();
    }
}

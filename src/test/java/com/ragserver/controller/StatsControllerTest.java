package com.ragserver.controller;

import com.ragserver.service.DocumentService;
import com.ragserver.service.ImageStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * StatsController 集成测试
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@WebMvcTest(StatsController.class)
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private ImageStorageService imageStorageService;

    /**
     * 测试：获取系统总览统计
     */
    @Test
    void testGetOverview() throws Exception {
        // Given: Mock统计数据
        DocumentService.CollectionStats collectionStats = new DocumentService.CollectionStats();
        collectionStats.setTotalDocuments(100L);
        collectionStats.setSuccessDocuments(95L);
        collectionStats.setFailedDocuments(5L);
        collectionStats.setVectorCount(1500L);

        ImageStorageService.StorageStats storageStats = new ImageStorageService.StorageStats();
        storageStats.setTotalImages(250L);

        when(documentService.getCollectionStats()).thenReturn(collectionStats);
        when(imageStorageService.getStats()).thenReturn(storageStats);

        // When & Then: 调用API
        mockMvc.perform(get("/api/stats/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments").value(100))
                .andExpect(jsonPath("$.successDocuments").value(95))
                .andExpect(jsonPath("$.failedDocuments").value(5))
                .andExpect(jsonPath("$.vectorCount").value(1500))
                .andExpect(jsonPath("$.imageCount").value(250));

        verify(documentService).getCollectionStats();
        verify(imageStorageService).getStats();
    }

    /**
     * 测试：获取系统总览统计 - 服务异常
     */
    @Test
    void testGetOverviewWithError() throws Exception {
        // Given: 服务抛出异常
        when(documentService.getCollectionStats()).thenThrow(new RuntimeException("Database error"));

        // When & Then: 返回500错误
        mockMvc.perform(get("/api/stats/overview"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());
    }

    /**
     * 测试：获取Collection统计
     */
    @Test
    void testGetCollections() throws Exception {
        // Given: Mock统计数据
        DocumentService.CollectionStats stats = new DocumentService.CollectionStats();
        stats.setTotalDocuments(50L);
        stats.setSuccessDocuments(48L);
        stats.setFailedDocuments(2L);
        stats.setVectorCount(800L);

        when(documentService.getCollectionStats()).thenReturn(stats);

        // When & Then: 调用API
        mockMvc.perform(get("/api/stats/collections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collectionName").exists())
                .andExpect(jsonPath("$.totalDocuments").value(50))
                .andExpect(jsonPath("$.successDocuments").value(48))
                .andExpect(jsonPath("$.failedDocuments").value(2))
                .andExpect(jsonPath("$.vectorCount").value(800));

        verify(documentService).getCollectionStats();
    }

    /**
     * 测试：获取Collection统计 - 服务异常
     */
    @Test
    void testGetCollectionsWithError() throws Exception {
        // Given: 服务抛出异常
        when(documentService.getCollectionStats()).thenThrow(new RuntimeException("Milvus error"));

        // When & Then: 返回500错误
        mockMvc.perform(get("/api/stats/collections"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());
    }
}

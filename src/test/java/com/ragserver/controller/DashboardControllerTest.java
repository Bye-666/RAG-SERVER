package com.ragserver.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DashboardController测试
 *
 * @author RAG-SERVER开发团队
 */
@WebMvcTest(DashboardController.class)
@DisplayName("DashboardController - Dashboard页面测试")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("测试1: 根路径重定向到Dashboard")
    void test1_RootRedirectsToDashboard() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    @DisplayName("测试2: Dashboard路径返回index.html")
    void test2_DashboardForwardsToIndex() throws Exception {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("/index.html"));
    }
}

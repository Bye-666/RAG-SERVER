package com.ragserver.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Dashboard页面控制器
 *
 * <p>提供前端Dashboard的访问入口：</p>
 * <ul>
 *   <li>/ - 重定向到Dashboard</li>
 *   <li>/dashboard - Dashboard主页</li>
 * </ul>
 *
 * @author RAG-SERVER开发团队
 * @since 1.0.0
 */
@Controller
public class DashboardController {

    /**
     * 根路径重定向到Dashboard
     */
    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    /**
     * Dashboard主页
     */
    @GetMapping("/dashboard")
    public String dashboard() {
        return "forward:/index.html";
    }
}

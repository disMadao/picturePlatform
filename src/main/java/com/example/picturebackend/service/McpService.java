package com.example.picturebackend.service;

import java.util.List;
import java.util.Map;

/**
 * @Auther: Mr. Mei
 * @Date: 2025/10/10 23:47
 * @Description:
 */
public interface McpService {
    public List<Map<String, Object>> getTools(); // 获得工具列表

    public Map<String, Object> callTool(String toolName, Map<String, Object> arguments); // 调用工具函数
}

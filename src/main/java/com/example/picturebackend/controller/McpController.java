package com.example.picturebackend.controller;

import com.example.picturebackend.manager.CosManager;
import com.example.picturebackend.service.McpService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * @Auther: Mr. Mei
 * @Date: 2025/10/10 22:42
 * @Description:
 */
@RestController
@RequestMapping("/mcp")
public class McpController {
    @Resource
    private McpService mcpService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public void handleGet() {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "不支持持久长连接");
    }

    @PostMapping
    public ResponseEntity<ObjectNode> handlePost(@RequestBody String body) throws Exception {
        ObjectNode request = objectMapper.readValue(body, ObjectNode.class);
        ResponseEntity<ObjectNode> response = null;

        String id = request.has("id") ? request.get("id").asText() : null;

        if (id == null) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(null);
        } else {
            String method = request.get("method").asText();
            switch (method) {
                case "initialize":
                    response = handleInitialize(id);
                    break;
                case "tools/list":
                    response = handleListTools(id);
                    break;
                case "tools/call":
                    response = handleCallTool(request);
                    break;
                default:
                    response = handleUnsupportedMethod(id, method);
                    break;
            }
        }

        return response;
    }

    private ResponseEntity<ObjectNode> handleUnsupportedMethod(String id, String method) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        ObjectNode error = response.putObject("error");
        error.put("code", -32601);
        error.put("message", "本服务器不支持这个方法");

        return ResponseEntity.badRequest().body(response);
    }

    private ResponseEntity<ObjectNode> handleInitialize(String id) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        ObjectNode result = response.putObject("result");
        result.put("protocolVersion", "2024-11-05");

        ObjectNode capabilities = result.putObject("capabilities");

        // 设置服务器信息
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "本地项目的图片查找服务-新版MCP协议");
        serverInfo.put("version", "1.0.0");

        return ResponseEntity.ok(response);
    }

    private ResponseEntity<ObjectNode> handleCallTool(ObjectNode request) throws Exception {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", request.get("id").asText());

        String toolName = request.get("params").get("name").asText();
        Map<String, Object> arguments = objectMapper.convertValue(request.get("params").get("arguments"), Map.class);
        Map<String, Object> result = mcpService.callTool(toolName, arguments); // 业务方法：调用工具函数
        response.set("result", objectMapper.valueToTree(result));

        return ResponseEntity.ok(response);
    }

    private ResponseEntity<ObjectNode> handleListTools(String id) throws Exception {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        ObjectNode result = response.putObject("result");

        List<Map<String, Object>> tools = mcpService.getTools();
        result.set("tools", objectMapper.valueToTree(tools));

        return ResponseEntity.ok(response);
    }
}

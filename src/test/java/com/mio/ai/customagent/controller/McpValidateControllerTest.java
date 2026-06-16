package com.mio.ai.customagent.controller;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;
import java.util.Map;


@SpringBootTest
class McpValidateControllerTest {

    @Test
    void validateMcpConfig() {
        String config = "{\n" +
                "  \"mcpServers\": {\n" +
                "    \"amap-maps\": {\n" +
                "      \"command\": \"npx.cmd\",\n" +
                "      \"args\": [\n" +
                "        \"-y\",\n" +
                "        \"@amap/amap-maps-mcp-server\"\n" +
                "      ],\n" +
                "      \"env\": {\n" +
                "        \"AMAP_MAPS_API_KEY\": \"4f139370b9f0de116bc60ce409506dae\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        JSONObject root = JSONUtil.parseObj(config);
        JSONObject mcpServers = root.getJSONObject("mcpServers");

        for (String serverName : mcpServers.keySet()) {
            JSONObject server = mcpServers.getJSONObject(serverName);

            String command = server.getStr("command");
            JSONArray argsArray = server.getJSONArray("args");
            JSONObject envObj = server.getJSONObject("env");

            List<String> args = argsArray.toList(String.class);
            Map<String, String> env = envObj.toBean(Map.class);

            System.out.println(serverName);
            System.out.println(command);
            System.out.println(args);
            System.out.println(env);

            test1(command, args, env);
        }
    }

    public void test1(String command, List<String> args, Map<String, String> env) {
        McpJsonMapper mapper = new JacksonMcpJsonMapper(new ObjectMapper());
        StdioClientTransport transport = new StdioClientTransport(
                ServerParameters.builder(command)
                        .args(args)
                        .env(env)
                        .build(),
                mapper
        );
        try (McpSyncClient client = McpClient.sync(transport)
                .clientInfo(
                        new McpSchema.Implementation("my-client", "1.0.0")
                )
                .capabilities(
                        McpSchema.ClientCapabilities.builder().roots(true).sampling().build()
                )
                .requestTimeout(Duration.ofSeconds(60))
                .build()) {
            McpSchema.InitializeResult initialize = client.initialize();
            System.out.println("client initialized: " + initialize);

            tools(client); // 打印 MCP 工具列表
        }
    }

    public void tools (McpSyncClient client) {
        McpSchema.ListToolsResult listToolsResult = client.listTools();
        listToolsResult.tools().forEach(System.out::println);
    }
}
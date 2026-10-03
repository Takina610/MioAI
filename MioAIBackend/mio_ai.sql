/*
 MioAI 初始化脚本（纯结构 + 种子数据）

 - 本脚本只包含表结构与最小种子数据（内置智能体 + 一个管理员账号），
   不包含任何真实聊天记录、文档、密钥或用户数据。
 - 默认管理员：账号 admin / 密码 admin123456（MD5 加盐存储，
   首次登录成功后会自动升级为 BCrypt，请登录后立即修改密码）。
 - 向量数据存于 PostgreSQL（pgvector），聊天记忆存于 spring_ai_chat_memory，
   均由应用启动时自动建表/建索引，无需在此维护。
*/

CREATE DATABASE IF NOT EXISTS mio_ai CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE mio_ai;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `user_account` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '账号',
  `user_password` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码（BCrypt，历史数据为MD5加盐，登录后自动升级）',
  `user_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户昵称',
  `user_avatar` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户头像',
  `user_profile` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户简介',
  `user_role` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'user' COMMENT '用户角色：user/admin',
  `edit_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '编辑时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_account`(`user_account` ASC) USING BTREE,
  INDEX `idx_user_name`(`user_name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of user（默认管理员，登录后请立即改密）
-- ----------------------------
INSERT INTO `user` VALUES (1, 'admin', 'c23c9945fc1f7030cc5acd8e2f378aee', '管理员', NULL, NULL, 'admin', NOW(), NOW(), NOW(), 0);

-- ----------------------------
-- Table structure for agent
-- ----------------------------
DROP TABLE IF EXISTS `agent`;
CREATE TABLE `agent`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NULL DEFAULT NULL COMMENT '创建者ID（NULL 表示系统内置）',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '智能体名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '智能体描述',
  `avatar` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '智能体头像',
  `type` tinyint NULL DEFAULT 0 COMMENT '类型:0-系统内置超级智能体,1-自定义智能体',
  `system_prompt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '系统提示词',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态:0-草稿,1-已发布,2-禁用',
  `is_public` tinyint NULL DEFAULT 0 COMMENT '是否公开:0-私有,1-公开',
  `usage_count` int NULL DEFAULT 0 COMMENT '使用次数',
  `version` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT '1.0.0' COMMENT '版本号',
  `is_deleted` tinyint NULL DEFAULT 0 COMMENT '是否删除:0-未删除,1-已删除',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_type_status`(`type` ASC, `status` ASC) USING BTREE,
  INDEX `idx_is_deleted`(`is_deleted` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '智能体表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of agent（唯一智能体 MioBot）
-- ----------------------------
INSERT INTO `agent` VALUES (1, NULL, 'MioBot', 'MioAI 智能助手：自主规划、调用工具、迭代执行的完整 Agent，具备联网搜索、网页抓取、文件生成、终端、任务清单等能力。', NULL, 0, NULL, 1, 1, 0, '2.0.0', 0, NOW(), NOW());

-- ----------------------------
-- Table structure for agent_usage_log
-- ----------------------------
DROP TABLE IF EXISTS `agent_usage_log`;
CREATE TABLE `agent_usage_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_id` bigint NOT NULL COMMENT '智能体ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `conversation_id` bigint NULL DEFAULT NULL COMMENT '会话ID',
  `input_tokens` int NULL DEFAULT 0 COMMENT '输入token数',
  `output_tokens` int NULL DEFAULT 0 COMMENT '输出token数',
  `cost` decimal(10, 6) NULL DEFAULT 0.000000 COMMENT '成本',
  `response_time` int NULL DEFAULT NULL COMMENT '响应时间(毫秒)',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态:0-失败,1-成功',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误信息',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_agent_id`(`agent_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '智能体使用记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for chat_conversation
-- ----------------------------
DROP TABLE IF EXISTS `chat_conversation`;
CREATE TABLE `chat_conversation`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `conversation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话ID（唯一）',
  `agent_id` bigint NULL DEFAULT NULL COMMENT '智能体ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '未命名对话' COMMENT '对话标题',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `UK_CONVERSATION_ID`(`conversation_id` ASC) USING BTREE,
  INDEX `IDX_USER_ID`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '智能体-对话会话表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for document
-- ----------------------------
DROP TABLE IF EXISTS `document`;
CREATE TABLE `document`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `kb_id` bigint NOT NULL COMMENT '知识库ID',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件名',
  `file_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件类型:pdf/docx/txt/md等',
  `file_size` bigint NULL DEFAULT NULL COMMENT '文件大小(字节)',
  `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '文件存储路径',
  `status` tinyint NULL DEFAULT 0 COMMENT '状态:0-上传中,1-处理中,2-已完成,3-失败',
  `is_deleted` tinyint NULL DEFAULT 0 COMMENT '是否删除:0-未删除,1-已删除',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_kb_id`(`kb_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_is_deleted`(`is_deleted` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文档表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for knowledge_base
-- ----------------------------
DROP TABLE IF EXISTS `knowledge_base`;
CREATE TABLE `knowledge_base`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '创建者ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '知识库名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '知识库描述',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态:0-构建中,1-就绪,2-失败',
  `is_public` int NULL DEFAULT 0 COMMENT '是否公开（0-私有 1-公开）',
  `document_count` int NULL DEFAULT 0 COMMENT '文档数量',
  `storage_size` bigint NULL DEFAULT 0 COMMENT '存储大小(字节)',
  `is_deleted` tinyint NULL DEFAULT 0 COMMENT '是否删除:0-未删除,1-已删除',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_is_deleted`(`is_deleted` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识库表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for mcp_tool
-- ----------------------------
DROP TABLE IF EXISTS `mcp_tool`;
CREATE TABLE `mcp_tool`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '创建者ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '工具名称，如: amap-maps',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '工具描述',
  `config` json NOT NULL COMMENT 'MCP配置，完整JSON配置（注意：其中的密钥属于敏感信息，禁止提交含真实密钥的数据）',
  `tool_info` json NOT NULL COMMENT 'MCP具体信息，如工具名称，工具列表',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态:0-禁用,1-启用',
  `is_public` tinyint NULL DEFAULT 0 COMMENT '是否公开:0-私有,1-公开',
  `is_deleted` tinyint NULL DEFAULT 0 COMMENT '是否删除:0-未删除,1-已删除',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_name`(`name` ASC) USING BTREE,
  INDEX `idx_is_deleted`(`is_deleted` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'MCP工具表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for rag_retrieval_log
-- ----------------------------
DROP TABLE IF EXISTS `rag_retrieval_log`;
CREATE TABLE `rag_retrieval_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_id` bigint NOT NULL COMMENT '智能体ID',
  `user_id` bigint NULL DEFAULT NULL COMMENT '用户ID',
  `kb_id` bigint NOT NULL COMMENT '知识库ID',
  `query` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '查询内容',
  `retrieved_chunks` json NULL COMMENT '检索到的块信息',
  `top_k` int NULL DEFAULT NULL COMMENT '检索数量',
  `score_threshold` float NULL DEFAULT NULL COMMENT '相似度阈值',
  `response_time` int NULL DEFAULT NULL COMMENT '检索耗时(毫秒)',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_agent_kb`(`agent_id` ASC, `kb_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'RAG检索记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tool_call_log
-- ----------------------------
DROP TABLE IF EXISTS `tool_call_log`;
CREATE TABLE `tool_call_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `agent_id` bigint NOT NULL COMMENT '智能体ID',
  `user_id` bigint NULL DEFAULT NULL COMMENT '用户ID',
  `tool_id` bigint NOT NULL COMMENT '工具ID',
  `conversation_id` bigint NULL DEFAULT NULL COMMENT '会话ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '工具名称',
  `input_params` json NULL COMMENT '输入参数',
  `output_result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '输出结果',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态:0-失败,1-成功,2-超时',
  `execution_time` int NULL DEFAULT NULL COMMENT '执行耗时(毫秒)',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误信息',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_agent_tool`(`agent_id` ASC, `tool_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_conversation_id`(`conversation_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '工具调用记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for spring_ai_chat_memory（Spring AI 自动管理）
-- ----------------------------
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    `conversation_id` VARCHAR(36) NOT NULL,
    `content` TEXT NOT NULL,
    `type` VARCHAR(10) NOT NULL,
    `timestamp` TIMESTAMP NOT NULL,
    `sequence_id` BIGINT NOT NULL,
    INDEX `SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX` (`conversation_id`, `timestamp`),
    INDEX `SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_SEQUENCE_ID_IDX` (`conversation_id`, `sequence_id`)
);

SET FOREIGN_KEY_CHECKS = 1;

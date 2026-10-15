-- ============================================================
-- 2026-10-15 管理后台增强
-- 1) 用户停用开关：user.status（1 正常 / 0 停用，登录时拦截）
-- 2) RAG 检索日志补会话与模型维度（管理端按会话/模型精确筛选）
-- ============================================================

ALTER TABLE `user`
    ADD COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-停用（停用后禁止登录）' AFTER `user_role`;

ALTER TABLE `rag_retrieval_log`
    ADD COLUMN `conversation_id` varchar(64) NULL DEFAULT NULL COMMENT '会话ID（群聊检索为空）' AFTER `kb_id`,
    ADD COLUMN `model` varchar(64) NULL DEFAULT NULL COMMENT '本轮对话模型（未指定为空）' AFTER `conversation_id`,
    ADD INDEX `idx_rag_conversation` (`conversation_id`),
    ADD INDEX `idx_rag_model` (`model`);

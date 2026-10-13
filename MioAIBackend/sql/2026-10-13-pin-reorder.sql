-- 2026-10-13 置顶与手动排序：
-- ① agent 增加 pinned（置顶标记）与 sort_order（组内排序，小在前）——智能体左轨长按拖动排序 + 置顶分组；
-- ② chat_conversation 增加 pinned——会话列表置顶（置顶组按置顶时间倒序在前，其余按更新时间倒序）。
-- 执行方式：mysql --default-character-set=utf8mb4 -uroot -p mio_ai < this-file.sql

ALTER TABLE `agent`
  ADD COLUMN `pinned` tinyint NULL DEFAULT 0 COMMENT '置顶:0-否,1-是' AFTER `is_public`,
  ADD COLUMN `sort_order` bigint NULL DEFAULT 0 COMMENT '手动排序序号(同置顶分组内小在前)' AFTER `pinned`;

ALTER TABLE `chat_conversation`
  ADD COLUMN `pinned` tinyint NULL DEFAULT 0 COMMENT '置顶:0-否,1-是' AFTER `title`;

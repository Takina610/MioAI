-- 2026-10-12 广场 MioBot 按用户呈现：
-- agent_user_config 增加 is_public：用户把自己的 MioBot 发布到广场的标记
-- （广场的 MioBot 槽位按当前用户装配：已发布显示自己的，游客显示官方内置）
-- 执行方式（防中文注释乱码）：
-- mysql --default-character-set=utf8mb4 -uroot -p mio_ai < this-file.sql

ALTER TABLE `agent_user_config`
  ADD COLUMN `is_public` tinyint NULL DEFAULT 0 COMMENT '是否发布到广场:0-未发布,1-已发布' AFTER `icon`;

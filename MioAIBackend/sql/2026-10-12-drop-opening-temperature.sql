-- 2026-10-12 删除「配置界面丰富化」整套字段（开场白/推荐问题/温度）：
-- 该功能经用户确认整体下线，相关列全部移除
-- 执行方式（防中文注释乱码）：
-- mysql --default-character-set=utf8mb4 -uroot -p mio_ai < this-file.sql

ALTER TABLE `agent`
  DROP COLUMN `opening_message`,
  DROP COLUMN `opening_questions`,
  DROP COLUMN `temperature`;

ALTER TABLE `agent_version`
  DROP COLUMN `opening_message`,
  DROP COLUMN `opening_questions`,
  DROP COLUMN `temperature`;

ALTER TABLE `agent_user_config`
  DROP COLUMN `opening_message`,
  DROP COLUMN `opening_questions`,
  DROP COLUMN `temperature`;

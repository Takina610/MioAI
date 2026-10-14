-- 模型设置演进：自定义供应商与当前选择改存浏览器本地（localStorage），后端不再持久化
-- 本次清理此前建的两张表（供应商配置含 API Key，按隐私要求仅存本地）；
-- 免费模型目录走反代 /models，聊天/档位探测的供应商端点配置随请求瞬态携带
DROP TABLE IF EXISTS user_model_provider;
DROP TABLE IF EXISTS user_model_setting;

-- ========================================
-- 迁移脚本：为 query_history 表添加缺失字段
-- ========================================
-- 执行时间：2026-11-12
-- 目的：使数据库表结构与 JPA 实体类一致

USE rag_server;

-- 添加新字段
ALTER TABLE query_history
ADD COLUMN question VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '用户问题' AFTER query_text,
ADD COLUMN answer TEXT COMMENT '生成的答案' AFTER question,
ADD COLUMN retrieved_docs_count INT COMMENT '检索到的文档数量' AFTER result_count,
ADD COLUMN query_type VARCHAR(50) COMMENT '查询类型' AFTER session_id,
ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'SUCCESS' COMMENT '查询状态' AFTER query_type,
ADD COLUMN error_message TEXT COMMENT '错误信息' AFTER status;

-- 修改字段类型
ALTER TABLE query_history
MODIFY COLUMN response_time_ms BIGINT COMMENT '响应时间（毫秒）';

-- 添加索引
ALTER TABLE query_history
ADD INDEX idx_status (status);

-- 验证表结构
DESCRIBE query_history;

-- 显示成功信息
SELECT '迁移成功：query_history 表已更新' AS result;

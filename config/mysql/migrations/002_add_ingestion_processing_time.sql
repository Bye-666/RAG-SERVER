-- ========================================
-- 迁移脚本：为 ingestion_history 表添加处理耗时字段
-- ========================================
-- 执行时间：2026-11-12
-- 目的：记录文档上传处理耗时，提升用户体验

USE rag_server;

-- 添加处理耗时字段
ALTER TABLE ingestion_history
ADD COLUMN processing_time_ms BIGINT COMMENT '处理耗时（毫秒）' AFTER chunk_count;

-- 验证表结构
DESCRIBE ingestion_history;

-- 显示成功信息
SELECT '迁移成功：ingestion_history 表已添加 processing_time_ms 字段' AS result;

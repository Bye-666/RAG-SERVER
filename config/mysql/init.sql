-- ========================================
-- RAG-SERVER MySQL 初始化脚本
-- ========================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS rag_server
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE rag_server;

-- ========================================
-- 表：ingestion_history（文档摄取历史）
-- ========================================
CREATE TABLE IF NOT EXISTS ingestion_history (
    file_hash VARCHAR(64) PRIMARY KEY COMMENT 'SHA256文件哈希',
    file_path VARCHAR(1024) NOT NULL COMMENT '文件路径',
    file_name VARCHAR(512) NOT NULL COMMENT '文件名',
    file_size BIGINT COMMENT '文件大小（字节）',
    collection_name VARCHAR(128) COMMENT 'Collection名称',
    status VARCHAR(20) NOT NULL COMMENT '状态：SUCCESS, FAILED, PROCESSING',
    chunk_count INT COMMENT 'Chunk数量',
    error_msg TEXT COMMENT '错误信息',
    processed_at TIMESTAMP NULL COMMENT '处理时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status (status),
    INDEX idx_collection (collection_name),
    INDEX idx_processed_at (processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='文档摄取历史记录表';

-- ========================================
-- 表：image_index（图片索引）
-- ========================================
CREATE TABLE IF NOT EXISTS image_index (
    image_id VARCHAR(64) PRIMARY KEY COMMENT '图片ID',
    file_path VARCHAR(1024) NOT NULL COMMENT '图片文件路径（本地文件系统）',
    collection_name VARCHAR(128) COMMENT 'Collection名称',
    doc_hash VARCHAR(64) COMMENT '关联的文档哈希',
    page_num INT COMMENT '页码',
    width INT COMMENT '图片宽度',
    height INT COMMENT '图片高度',
    caption TEXT COMMENT '图片描述（Vision LLM生成）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_doc_hash (doc_hash),
    INDEX idx_collection (collection_name),
    FOREIGN KEY (doc_hash) REFERENCES ingestion_history(file_hash) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='图片索引表';

-- ========================================
-- 表：query_history（查询历史）
-- ========================================
CREATE TABLE IF NOT EXISTS query_history (
    query_id VARCHAR(64) PRIMARY KEY COMMENT '查询ID',
    query_text TEXT NOT NULL COMMENT '查询文本',
    collection_name VARCHAR(128) COMMENT 'Collection名称',
    top_k INT DEFAULT 10 COMMENT '返回结果数量',
    enable_rerank BOOLEAN DEFAULT FALSE COMMENT '是否启用Rerank',
    result_count INT COMMENT '实际返回结果数量',
    response_time_ms INT COMMENT '响应时间（毫秒）',
    user_id VARCHAR(128) COMMENT '用户ID（可选）',
    session_id VARCHAR(128) COMMENT '会话ID（可选）',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '查询时间',
    INDEX idx_collection (collection_name),
    INDEX idx_user (user_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='查询历史记录表';

-- ========================================
-- 表：collection_stats（Collection统计）
-- ========================================
CREATE TABLE IF NOT EXISTS collection_stats (
    collection_name VARCHAR(128) PRIMARY KEY COMMENT 'Collection名称',
    document_count INT DEFAULT 0 COMMENT '文档数量',
    chunk_count BIGINT DEFAULT 0 COMMENT 'Chunk数量',
    total_size_bytes BIGINT DEFAULT 0 COMMENT '总大小（字节）',
    last_ingestion_at TIMESTAMP NULL COMMENT '最后摄取时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Collection统计信息表';

-- ========================================
-- 初始化数据
-- ========================================

-- 插入默认Collection
INSERT IGNORE INTO collection_stats (collection_name, document_count, chunk_count, total_size_bytes)
VALUES ('rag_knowledge_hub', 0, 0, 0);

-- ========================================
-- 查看表结构
-- ========================================
SHOW TABLES;

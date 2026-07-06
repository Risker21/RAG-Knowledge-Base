CREATE DATABASE IF NOT EXISTS rag_kb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE rag_kb;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` bigint PRIMARY KEY AUTO_INCREMENT,
    `username` varchar(64) NOT NULL,
    `password` varchar(256) NOT NULL,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 知识库表
CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id` bigint PRIMARY KEY AUTO_INCREMENT,
    `user_id` bigint NOT NULL,
    `name` varchar(128) NOT NULL,
    `description` text,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 文档表
CREATE TABLE IF NOT EXISTS `document` (
    `id` bigint PRIMARY KEY AUTO_INCREMENT,
    `kb_id` bigint NOT NULL,
    `user_id` bigint NOT NULL,
    `filename` varchar(256) NOT NULL,
    `file_type` varchar(16) NOT NULL,
    `file_path` varchar(512) NOT NULL,
    `file_size` bigint DEFAULT 0,
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '0=待处理 1=处理中 2=已完成 3=失败',
    `chunk_count` int DEFAULT 0,
    `error_msg` text,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`kb_id`) REFERENCES `knowledge_base`(`id`),
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 文档切片表（存文本 + 向量）
CREATE TABLE IF NOT EXISTS `doc_chunk` (
    `id` bigint PRIMARY KEY AUTO_INCREMENT,
    `doc_id` bigint NOT NULL,
    `kb_id` bigint NOT NULL,
    `chunk_index` int NOT NULL,
    `content` text NOT NULL,
    `embedding` text COMMENT 'JSON float 数组',
    FOREIGN KEY (`doc_id`) REFERENCES `document`(`id`),
    INDEX `idx_kb` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 对话表
CREATE TABLE IF NOT EXISTS `conversation` (
    `id` bigint PRIMARY KEY AUTO_INCREMENT,
    `user_id` bigint NOT NULL,
    `kb_id` bigint NOT NULL,
    `title` varchar(256) DEFAULT NULL,
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`),
    FOREIGN KEY (`kb_id`) REFERENCES `knowledge_base`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 消息表
CREATE TABLE IF NOT EXISTS `message` (
    `id` bigint PRIMARY KEY AUTO_INCREMENT,
    `conversation_id` bigint NOT NULL,
    `role` tinyint NOT NULL COMMENT '0=用户 1=AI',
    `content` text NOT NULL,
    `references_json` json DEFAULT NULL COMMENT '引用列表',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (`conversation_id`) REFERENCES `conversation`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

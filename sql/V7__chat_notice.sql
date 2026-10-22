-- 文件路径: sql/V7__chat_notice.sql
-- 模块6：消息与通知。三张表：chat_session（会话）、chat_message（消息）、notice（系统通知）
-- 说明：
--   1) 项目统一使用 create_time / update_time（需求文档中的 created_at / updated_at 同义）；
--   2) chat_session 的唯一键建在生成列 product_key 上，而不是直接建在 (buyer_id, seller_id, product_id) 上：
--      MySQL 唯一索引把多个 NULL 视为互不相等，product_id 可空时就无法保证「同一对买卖家只有一个通用会话」。
--      product_key = IFNULL(product_id, 0) 是 STORED 生成列，由数据库自动维护，业务代码无需赋值；
--   3) 「删除会话」只删自己这一侧的列表，因此用 buyer_deleted / seller_deleted 两个标记，
--      而不是逻辑删除整行——对方还需要看到这个会话；
--   4) chat_message 的索引建在 (session_id, id) 而不是 (session_id, create_time)：
--      自增主键与时间同序，游标分页用 id 做游标不会因同秒多条而重复或漏数据，且能覆盖排序；
--   5) notice 采用「一人一行」的扇形写入（系统公告也按用户拆行），这样已读状态天然按人隔离，
--      不需要额外的已读关联表；管理端发布公告时的用户量级在本系统中完全可控。
--   6) 三张表均用 CREATE TABLE IF NOT EXISTS，脚本可重复执行且不会清空已有数据。

USE campus_market;

-- ---------------------------------------------------------------
-- 1. 会话
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_session (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    buyer_id       BIGINT UNSIGNED NOT NULL COMMENT '买家ID（发起咨询的一方）',
    seller_id      BIGINT UNSIGNED NOT NULL COMMENT '卖家ID（被咨询的一方）',
    product_id     BIGINT UNSIGNED NULL COMMENT '关联商品ID，NULL 表示与商品无关的通用咨询',
    product_key    BIGINT UNSIGNED GENERATED ALWAYS AS (IFNULL(product_id, 0)) STORED COMMENT '生成列：把 NULL 归一为 0，供唯一索引使用',
    last_message   VARCHAR(120)    NULL COMMENT '最后一条消息摘要，会话列表直接展示，避免每行再查子表',
    last_time      DATETIME        NULL COMMENT '最后消息时间，会话列表按此倒序',
    buyer_unread   INT             NOT NULL DEFAULT 0 COMMENT '买家未读数',
    seller_unread  INT             NOT NULL DEFAULT 0 COMMENT '卖家未读数',
    buyer_deleted  TINYINT         NOT NULL DEFAULT 0 COMMENT '买家是否从自己列表删除:0否 1是',
    seller_deleted TINYINT         NOT NULL DEFAULT 0 COMMENT '卖家是否从自己列表删除:0否 1是',
    create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_pair_product (buyer_id, seller_id, product_key) COMMENT '同一对买卖家针对同一商品只有一个会话',
    KEY idx_buyer_list (buyer_id, buyer_deleted, last_time) COMMENT '「我作为买家」的会话列表',
    KEY idx_seller_list (seller_id, seller_deleted, last_time) COMMENT '「我作为卖家」的会话列表',
    KEY idx_product (product_id) COMMENT '商品详情 / 我的发布里查咨询该商品的会话'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '聊天会话';

-- ---------------------------------------------------------------
-- 2. 聊天消息
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_message (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id  BIGINT UNSIGNED NOT NULL COMMENT '所属会话ID',
    sender_id   BIGINT UNSIGNED NOT NULL COMMENT '发送者ID',
    receiver_id BIGINT UNSIGNED NOT NULL COMMENT '接收者ID，冗余存储便于按人统计未读',
    msg_type    TINYINT         NOT NULL DEFAULT 1 COMMENT '消息类型:1文本 2图片 3系统消息 4商品卡片 5订单卡片',
    content     TEXT            NOT NULL COMMENT '文本内容 / 图片URL / 卡片JSON',
    is_read     TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已读:0未读 1已读',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (id),
    KEY idx_session (session_id, id) COMMENT '会话内历史消息游标分页：WHERE session_id=? AND id<? ORDER BY id DESC',
    KEY idx_receiver_unread (receiver_id, is_read) COMMENT '全局未读统计'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '聊天消息';

-- ---------------------------------------------------------------
-- 3. 系统通知
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notice (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '接收用户ID',
    type        TINYINT         NOT NULL DEFAULT 1 COMMENT '类型:1订单通知 2审核通知 3系统公告 4举报处理',
    title       VARCHAR(60)     NOT NULL COMMENT '标题',
    content     VARCHAR(500)    NOT NULL COMMENT '正文',
    related_id  BIGINT UNSIGNED NULL COMMENT '关联业务ID（订单ID / 商品ID / 认证ID），用于「查看订单」跳转',
    is_read     TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已读:0未读 1已读',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_unread (user_id, is_read, create_time) COMMENT '我的通知：未读优先 + 时间倒序',
    KEY idx_type_time (type, create_time) COMMENT '管理端按类型筛选公告'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '系统通知';

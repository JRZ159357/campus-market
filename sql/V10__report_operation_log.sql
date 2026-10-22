-- =============================================================
-- 模块 9 · 举报与客服工单
-- 执行：mysql -uroot -p --default-character-set=utf8mb4 < sql/V10__report_operation_log.sql
--
-- 设计说明：
--   1) report 表一次承载三类举报（商品 / 用户 / 评价），靠 report_type 区分，
--      不拆成三张表：三者字段结构完全一致（举报人、原因、描述、证据、状态流转），
--      拆表会让「客服接单列表」必须 UNION 三张表，排序与分页都要写三遍。
--   2) target_snapshot 是本模块的核心设计：把被举报对象在举报时刻的关键信息
--      序列化成 JSON 存下来。为什么必须存：
--        - 违规商品会被强制下架、违规评价会被逻辑删除、违规用户会改名换头像，
--          客服过两天再处理时原对象可能已经面目全非甚至查不到；
--        - 举报成立与否是「当时的判断」，事后用可变对象举证会失真；
--        - 举报人删了商品也一样，快递类纠纷里卖家下架商品后举报记录就没内容可看了。
--      代价是 JSON 冗余（本系统单条约 200~500 字节），换来的是取证闭环。
--   3) reason 用 tinyint 枚举而不是 varchar：管理端要按原因统计，
--      字符串分组会让 '虚假描述' 与 '虚假描述 ' 算成两类；
--      展示文案由后端 ReportReason.label() 输出，改文案不用洗数据。
--   4) handle_action 单独一列而不是塞进 handle_result 的文字里：
--      「处理动作」是结构化事实（是否下架、扣了几分），要能被统计与复盘；
--      handle_result 是给举报人和被举报人看的说明文本。两者职责不同。
--   5) accept_time 与 create_time 分开记：工作台的「平均处理时长」
--      必须只统计客服自己花掉的时间。如果只用 create_time 起算，
--      一条在队列里躺了 3 天才被接的单会显示「处理时长 72 小时」，
--      这个数字会逼着客服去抢单而不是把单处理干净，指标反而失效了。
--   6) sys_operation_log 用 AOP 自动记录，写操作（举报处理、封禁、
--      角色变更、商品强制下架、评价删除）都会留一条。
--      放在本模块建表是因为举报处理是第一个"必须可追溯"的写操作。
--   7) 脚本可重复执行：CREATE TABLE IF NOT EXISTS。
-- =============================================================

USE `campus_market`;

-- -------------------------------------------------------------
-- 1. 举报表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `report` (
  `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `reporter_id`     bigint       NOT NULL                COMMENT '举报人ID',
  `report_type`     tinyint      NOT NULL                COMMENT '举报类型:1商品 2用户 3评价',
  `target_id`       bigint       NOT NULL                COMMENT '被举报对象ID（商品ID/用户ID/评价ID）',
  `target_user_id`  bigint                DEFAULT NULL   COMMENT '被举报人ID（对象归属人，冗余用于统计与通知）',
  `target_snapshot` json                  DEFAULT NULL   COMMENT '被举报对象快照JSON（标题/昵称/评价内容/图片等）',
  `reason`          tinyint               DEFAULT NULL   COMMENT '举报原因:1虚假描述 2违禁品 3欺诈 4辱骂骚扰 5广告 6其他',
  `content`         varchar(500) NOT NULL                COMMENT '详细描述（必填，至少5字）',
  `images`          varchar(1000)         DEFAULT NULL   COMMENT '证据图片URL，逗号分隔，最多3张',
  `status`          tinyint      NOT NULL DEFAULT 0      COMMENT '状态:0待处理 1处理中 2已处理 3已驳回',
  `handler_id`      bigint                DEFAULT NULL   COMMENT '处理人ID（客服/管理员）',
  `accept_time`     datetime              DEFAULT NULL   COMMENT '接单时间（客服点击接单的时刻）',
  `handle_result`   varchar(500)          DEFAULT NULL   COMMENT '处理结果描述',
  `handle_action`   tinyint      NOT NULL DEFAULT 0      COMMENT '处理动作:0不处理 1下架商品 2封禁用户 3删除评价 4扣除信誉分',
  `handle_time`     datetime              DEFAULT NULL   COMMENT '处理时间',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         tinyint      NOT NULL DEFAULT 0      COMMENT '逻辑删除:0正常 1删除',
  PRIMARY KEY (`id`),
  -- 客服工作台的默认视图就是「按状态筛选 + 按时间倒序」，
  -- 这个组合索引让待处理列表永远走索引前缀，不必 filesort 全表
  KEY `idx_status_create` (`status`, `create_time`),
  -- 我的举报列表
  KEY `idx_reporter` (`reporter_id`, `create_time`),
  -- 判断「这个对象被举报过几次」「同一对象的多条举报一起处理」
  KEY `idx_type_target` (`report_type`, `target_id`),
  -- 按被举报人统计（一个卖家被举报了多少次）
  KEY `idx_target_user` (`target_user_id`),
  -- 「同一用户对同一对象 24 小时内只能举报一次」的判定走这条索引
  KEY `idx_reporter_type_target` (`reporter_id`, `report_type`, `target_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='举报表';

-- -------------------------------------------------------------
-- 2. 操作日志表
--    为什么不是「在每张业务表里加个操作人」：那样只能回答「谁最后改的」，
--    回答不了「这个管理员今天一共做了什么」。集中式日志表让管理端
--    能按操作人 / 模块 / 时间做全景检索，是答辩时「可追溯性」的直接体现。
--    注意 detail 存的是可读描述而不是参数 JSON：出事后要给人看，
--    给人看的中文比给人看的一串 {"userId":1} 更有用。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_operation_log` (
  `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `operator_id`    bigint                DEFAULT NULL   COMMENT '操作人ID',
  `operator_role`  varchar(20)           DEFAULT NULL   COMMENT '操作人角色码（user/student/customer_service/admin/system）',
  `operator_name`  varchar(50)           DEFAULT NULL   COMMENT '操作人昵称（冗余，改名后日志仍可读）',
  `module`         varchar(30)  NOT NULL                COMMENT '业务模块:report/user/product/review/order/student_auth',
  `action`         varchar(30)  NOT NULL                COMMENT '动作:accept/handle/offline/ban/update_role/delete',
  `target_id`      bigint                DEFAULT NULL   COMMENT '操作目标ID',
  `detail`         varchar(500)          DEFAULT NULL   COMMENT '操作详情（可读描述）',
  `ip`             varchar(50)           DEFAULT NULL   COMMENT '操作来源IP',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_operator` (`operator_id`, `create_time`),
  KEY `idx_module` (`module`, `create_time`),
  KEY `idx_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统操作日志表';
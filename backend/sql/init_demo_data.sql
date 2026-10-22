-- =============================================================
-- 校园二手交易平台 · 演示数据初始化脚本（毕设答辩用）
-- 文件：backend/sql/init_demo_data.sql
-- 依赖：V1 ~ V12 全部脚本已执行（库结构与预置分类、敏感词、首页配置已存在）
--
-- 用法：
--   Navicat / 命令行 直接整个文件执行即可，可重复执行：
--     mysql -uroot -p --default-character-set=utf8mb4 campus_market < backend/sql/init_demo_data.sql
--
--   ⚠️ 本脚本会【清空】以下业务表：商品、订单、订单日志、评价、信誉分流水、
--      收藏、关注、聊天会话与消息、举报、通知、地址、学生认证、轮播图，
--      以及除 admin / service01 / service02 之外的所有用户。
--      仅保留：后台账号 3 个、分类 7 个、敏感词 20 条、首页配置（公告文案会改写）。
--      只适用于演示 / 开发环境，切勿在有真实数据的库上执行。
--
-- 设计说明：
--   1) 所有演示数据使用固定 ID 段（9001 起），重跑时先按同样的规则清理再插入，
--      因此可以反复执行而不会重复堆数据；显式 ID 也能让订单、评价、举报之间的
--      引用关系在脚本里一眼可见（订单 9007 买的是商品 9010，评价 9015 评的是订单 9020）。
--   2) 时间一律用 NOW() 相对计算，任何一天执行都能得到「近 3 个月注册 / 近 2 个月发布 /
--      近 1 个月下单」的时间线，不会因为脚本放久了而变成一堆 2024 年的数据。
--   3) 商品状态由订单真实驱动：待发货/待收货/已完成/退款中的订单会把商品置为「已售出」，
--      已取消的订单把商品放回「在售」。这样点开一件商品看到的状态和订单是自洽的。
--   4) 图片使用 backend/uploads/demo/ 下的 6 张彩色封面与 12 张头像（纯代码生成，无版权问题）。
-- =============================================================

USE `campus_market`;
SET NAMES utf8mb4;

-- 演示账号统一密码：直接复用管理员密码的 BCrypt 值（admin 的密码为 123456），
-- 这样不必在脚本里硬编码哈希，也保证改过 admin 密码的环境里仍然能登录。
SET @pwd = (SELECT `password` FROM `sys_user` WHERE `id` = 1);


-- =============================================================
-- 0. 清理旧数据（顺序：子表 → 主表，避免残留孤儿行）
-- =============================================================
DELETE FROM `order_log`;
DELETE FROM `trade_order`;
DELETE FROM `review`;
DELETE FROM `credit_log`;
DELETE FROM `favorite`;
DELETE FROM `follow`;
DELETE FROM `chat_message`;
DELETE FROM `chat_session`;
DELETE FROM `report`;
DELETE FROM `notice`;
DELETE FROM `product_image`;
DELETE FROM `product`;
DELETE FROM `address`;
DELETE FROM `student_auth`;
DELETE FROM `banner`;
-- 只保留三个后台账号（admin / service01 / service02），其余用户全部清掉
DELETE FROM `sys_user` WHERE `role` IN ('user', 'student');
DELETE FROM `sys_user` WHERE `role` = 'customer_service' AND `id` NOT IN (2, 29);


-- =============================================================
-- 1. 测试用户 12 个
--    6 个普通用户（9001~9006，只能买，role=user）
--    6 个认证学生（9007~9012，能买能卖，role=student + student_verified=1）
--    注册时间分散在近 3 个月；信誉分按需求分布：
--      120+ 两人（9001、9007）、100 四人（9002/9003/9008/9009）、
--      80-99 三人（9004/9010/9011）、60-79 两人（9005/9006）、<60 一人（9012）
-- =============================================================
INSERT INTO `sys_user`
(`id`, `open_id`, `username`, `password`, `register_type`, `nickname`, `avatar`, `gender`, `intro`,
 `phone`, `role`, `status`, `student_verified`, `credit_score`, `publish_limit`,
 `last_login_time`, `create_time`, `update_time`, `deleted`)
VALUES
-- ---------- 普通用户 6 个 ----------
(9001, 'demo_open_01', 'linxiaoman', @pwd, 2, '林小满', '/uploads/demo/avatar-1.png', 2,
 '大二在读，喜欢淘二手书和桌面小物件，买卖都讲信用，随时可以约在图书馆门口看货。',
 '13800009001', 'user', 0, 0, 128, 0,
 DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 84 DAY), NOW(), 0),

(9002, 'demo_open_02', 'zhouzihao', @pwd, 2, '周子豪', '/uploads/demo/avatar-2.png', 1,
 '男生宿舍四号楼，主打数码和球鞋，晚上八点后基本都在线，能当面交易的优先。',
 '13800009002', 'user', 0, 0, 100, 0,
 DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 76 DAY), NOW(), 0),

(9003, 'demo_open_03', 'xuzhiyuan', @pwd, 2, '许知远', '/uploads/demo/avatar-3.png', 1,
 '大三考研党，专业书常年囤积又常年出清，欢迎同专业的同学来交换资料。',
 '13800009003', 'user', 0, 0, 100, 0,
 DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 68 DAY), NOW(), 0),

(9004, 'demo_open_04', 'chenjiahe', @pwd, 2, '陈嘉禾', '/uploads/demo/avatar-4.png', 2,
 '刚搬宿舍，正在陆续添置生活用品，遇到合适的就拍，付款很爽快。',
 '13800009004', 'user', 0, 0, 92, 0,
 DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 60 DAY), NOW(), 0),

(9005, 'demo_open_05', 'suxiaotong', @pwd, 2, '苏晓彤', '/uploads/demo/avatar-5.png', 2,
 '喜欢布置宿舍，买过不少台灯和收纳，闲置也会挂在平台上，喜欢当面验货再付款。',
 '13800009005', 'user', 0, 0, 72, 0,
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 52 DAY), NOW(), 0),

(9006, 'demo_open_06', 'hesiyuan', @pwd, 2, '何思远', '/uploads/demo/avatar-6.png', 1,
 '爱好骑行和露营，装备党一枚。近期因为一次交易纠纷被平台处理过，正在申诉。',
 '13800009006', 'user', 1, 0, 65, 0,
 DATE_SUB(NOW(), INTERVAL 26 DAY), DATE_SUB(NOW(), INTERVAL 30 DAY), NOW(), 0),

-- ---------- 认证学生 6 个 ----------
(9007, 'demo_open_07', 'wangzihan', @pwd, 2, '王梓涵', '/uploads/demo/avatar-7.png', 1,
 '计算机学院大四，毕业清仓中。数码外设和闲置衣物都在出，支持当面验货、可小刀。',
 '13800009007', 'student', 0, 1, 135, 20,
 DATE_SUB(NOW(), INTERVAL 12 HOUR), DATE_SUB(NOW(), INTERVAL 86 DAY), NOW(), 0),

(9008, 'demo_open_08', 'lisiyuan', @pwd, 2, '李思远', '/uploads/demo/avatar-8.png', 1,
 '机械学院大三，考研资料和专业课教材常年低价出，买多可以再便宜一点。',
 '13800009008', 'student', 0, 1, 100, 20,
 DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 79 DAY), NOW(), 0),

(9009, 'demo_open_09', 'zhangyutong', @pwd, 2, '张雨桐', '/uploads/demo/avatar-9.png', 2,
 '文学院大四，宿舍要清空了。生活用品基本都是九成新，价格好商量，女生宿舍楼下自提。',
 '13800009009', 'student', 0, 1, 100, 20,
 DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 72 DAY), NOW(), 0),

(9010, 'demo_open_10', 'zhaoyiming', @pwd, 2, '赵一鸣', '/uploads/demo/avatar-10.png', 1,
 '体育学院大二，球类装备、护具、球鞋都有，用过的东西会如实写清磨损情况。',
 '13800009010', 'student', 0, 1, 86, 20,
 DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 64 DAY), NOW(), 0),

(9011, 'demo_open_11', 'liuxingyu', @pwd, 2, '刘星宇', '/uploads/demo/avatar-11.png', 1,
 '在校外租房，代步工具换过好几轮，电动车、自行车、滑板车都卖过，配件也一起出。',
 '13800009011', 'student', 0, 1, 81, 20,
 DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 57 DAY), NOW(), 0),

(9012, 'demo_open_12', 'sunjianing', @pwd, 2, '孙嘉宁', '/uploads/demo/avatar-12.png', 2,
 '大三在读。之前有两笔交易因为描述不清引起过误会，已经按平台要求补充说明。',
 '13800009012', 'student', 0, 1, 55, 20,
 DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 41 DAY), NOW(), 0);


-- =============================================================
-- 2. 学生认证记录（6 个认证学生，全部审核通过）
--    real_name 与昵称刻意不完全一致（现实中也是这样），cert_img 用演示封面图占位
-- =============================================================
INSERT INTO `student_auth`
(`id`, `user_id`, `student_no`, `real_name`, `school`, `cert_img`, `status`,
 `auditor_id`, `audit_time`, `create_time`, `update_time`, `deleted`)
VALUES
(9001, 9007, '2022030412', '王梓涵', '北华大学', '/uploads/demo/cover-1.png', 1, 1,
 DATE_SUB(NOW(), INTERVAL 85 DAY), DATE_SUB(NOW(), INTERVAL 86 DAY), DATE_SUB(NOW(), INTERVAL 85 DAY), 0),
(9002, 9008, '2022040917', '李思远', '北华大学', '/uploads/demo/cover-2.png', 1, 1,
 DATE_SUB(NOW(), INTERVAL 78 DAY), DATE_SUB(NOW(), INTERVAL 79 DAY), DATE_SUB(NOW(), INTERVAL 78 DAY), 0),
(9003, 9009, '2021010725', '张雨桐', '北华大学', '/uploads/demo/cover-3.png', 1, 1,
 DATE_SUB(NOW(), INTERVAL 71 DAY), DATE_SUB(NOW(), INTERVAL 72 DAY), DATE_SUB(NOW(), INTERVAL 71 DAY), 0),
(9004, 9010, '2022050331', '赵一鸣', '北华大学', '/uploads/demo/cover-4.png', 1, 1,
 DATE_SUB(NOW(), INTERVAL 63 DAY), DATE_SUB(NOW(), INTERVAL 64 DAY), DATE_SUB(NOW(), INTERVAL 63 DAY), 0),
(9005, 9011, '2021030508', '刘星宇', '北华大学', '/uploads/demo/cover-5.png', 1, 1,
 DATE_SUB(NOW(), INTERVAL 56 DAY), DATE_SUB(NOW(), INTERVAL 57 DAY), DATE_SUB(NOW(), INTERVAL 56 DAY), 0),
(9006, 9012, '2022020846', '孙嘉宁', '北华大学', '/uploads/demo/cover-6.png', 1, 1,
 DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 41 DAY), DATE_SUB(NOW(), INTERVAL 40 DAY), 0);


-- =============================================================
-- 3. 收货地址（12 个用户各一条，默认地址）
--    校内地址写在宿舍区，方便演示「约在宿舍楼下自提」的聊天内容
-- =============================================================
INSERT INTO `address`
(`id`, `user_id`, `receiver_name`, `phone`, `address`, `is_default`, `create_time`, `update_time`, `deleted`)
VALUES
(9001, 9001, '林小满', '13800009001', '北华大学 东校区 6 号宿舍楼 412 室', 1, DATE_SUB(NOW(), INTERVAL 80 DAY), NOW(), 0),
(9002, 9002, '周子豪', '13800009002', '北华大学 东校区 4 号宿舍楼 208 室', 1, DATE_SUB(NOW(), INTERVAL 72 DAY), NOW(), 0),
(9003, 9003, '许知远', '13800009003', '北华大学 西校区 9 号宿舍楼 615 室', 1, DATE_SUB(NOW(), INTERVAL 64 DAY), NOW(), 0),
(9004, 9004, '陈嘉禾', '13800009004', '北华大学 西校区 3 号宿舍楼 107 室', 1, DATE_SUB(NOW(), INTERVAL 56 DAY), NOW(), 0),
(9005, 9005, '苏晓彤', '13800009005', '北华大学 东校区 8 号宿舍楼 302 室', 1, DATE_SUB(NOW(), INTERVAL 48 DAY), NOW(), 0),
(9006, 9006, '何思远', '13800009006', '北华大学 南校区 2 号宿舍楼 519 室', 1, DATE_SUB(NOW(), INTERVAL 26 DAY), NOW(), 0),
(9007, 9007, '王梓涵', '13800009007', '北华大学 东校区 1 号宿舍楼 305 室', 1, DATE_SUB(NOW(), INTERVAL 82 DAY), NOW(), 0),
(9008, 9008, '李思远', '13800009008', '北华大学 西校区 7 号宿舍楼 224 室', 1, DATE_SUB(NOW(), INTERVAL 75 DAY), NOW(), 0),
(9009, 9009, '张雨桐', '13800009009', '北华大学 东校区 5 号宿舍楼 618 室', 1, DATE_SUB(NOW(), INTERVAL 68 DAY), NOW(), 0),
(9010, 9010, '赵一鸣', '13800009010', '北华大学 南校区 1 号宿舍楼 402 室', 1, DATE_SUB(NOW(), INTERVAL 60 DAY), NOW(), 0),
(9011, 9011, '刘星宇', '13800009011', '北华大学 校外 学府路 12 号 晨光公寓 3 单元 502', 1, DATE_SUB(NOW(), INTERVAL 53 DAY), NOW(), 0),
(9012, 9012, '孙嘉宁', '13800009012', '北华大学 西校区 2 号宿舍楼 411 室', 1, DATE_SUB(NOW(), INTERVAL 37 DAY), NOW(), 0);


-- =============================================================
-- 4. 信誉分流水（只为「不等于 100」的用户补流水，before/after 严格串起来）
--    信誉分管理的核心是「分数怎么来的」，只改分值不留流水，页面上一查就是空的
-- =============================================================
INSERT INTO `credit_log`
(`id`, `user_id`, `change_score`, `before_score`, `after_score`, `type`, `reason`,
 `related_id`, `operator_id`, `create_time`)
VALUES
(9001, 9001, 28, 100, 128, 3, '平台信用调整：历史交易全部按时履约，买家评价均为五星', NULL, 1,
 DATE_SUB(NOW(), INTERVAL 20 DAY)),
(9002, 9007, 35, 100, 135, 3, '平台信用调整：累计完成交易 12 笔，无一笔纠纷', NULL, 1,
 DATE_SUB(NOW(), INTERVAL 18 DAY)),
(9003, 9004, -8, 100, 92, 2, '举报处理：举报成立（商品描述与实际不符），扣除信誉分', NULL, 1,
 DATE_SUB(NOW(), INTERVAL 22 DAY)),
(9004, 9010, -14, 100, 86, 2, '举报处理：举报成立（商品违规下架），扣除信誉分', NULL, 1,
 DATE_SUB(NOW(), INTERVAL 25 DAY)),
(9005, 9011, -19, 100, 81, 3, '平台信用调整：两笔订单发货超时，按规则扣减', NULL, 1,
 DATE_SUB(NOW(), INTERVAL 15 DAY)),
(9006, 9005, -28, 100, 72, 2, '举报处理：举报成立（未按约定发货），扣除信誉分', NULL, 1,
 DATE_SUB(NOW(), INTERVAL 12 DAY)),
(9007, 9006, -10, 100, 90, 2, '举报处理：举报成立（虚假描述），扣除信誉分', NULL, 1,
 DATE_SUB(NOW(), INTERVAL 9 DAY)),
(9008, 9006, -25, 90, 65, 3, '平台信用调整：账号封禁期间继续被举报，追加扣减', NULL, 1,
 DATE_SUB(NOW(), INTERVAL 8 DAY)),
(9009, 9012, -10, 100, 90, 2, '举报处理：举报成立（描述与实际不符），扣除信誉分', NULL, 1,
 DATE_SUB(NOW(), INTERVAL 14 DAY)),
(9010, 9012, -35, 90, 55, 3, '平台信用调整：同一问题被二次举报，追加扣减', NULL, 1,
 DATE_SUB(NOW(), INTERVAL 6 DAY));


-- =============================================================
-- 5. 商品 40 件（覆盖 7 个分类，每类 5-6 件）
--    状态分布：17 件在售 / 18 件已售出 / 2 件已下架 / 1 件违规下架 / 2 件待审核
--    说明：已售出的 18 件与 18 笔「占用中」订单（待发货 3 + 待收货 4 + 已完成 9 +
--          退款中 2）严格一一对应，取消的订单把商品放回在售。这样点开商品的
--          「已售出」和订单列表里的记录是自洽的，不会出现一件商品被卖两次。
--    发布人：9007 王梓涵（7 件）、9008 李思远（7 件）、9009 张雨桐（7 件）、
--            9010 赵一鸣（7 件）、9011 刘星宇（6 件）、9012 孙嘉宁（6 件）
-- =============================================================
INSERT INTO `product`
(`id`, `seller_id`, `category_id`, `title`, `description`, `price`, `original_price`,
 `status`, `view_count`, `favorite_count`, `is_verified_seller`, `need_manual_review`,
 `sensitive_hit`, `audit_reason`, `auditor_id`, `audit_time`, `create_time`, `update_time`, `deleted`)
VALUES
-- ---------- 数码电子（分类 1）：9001~9006 ----------
(9001, 9007, 1, 'iPad 2021 第九代 64G 深空灰',
 '去年九月买的，一直在用来看网课和做笔记，屏幕无划痕，边框有一点点使用痕迹。原装充电器和数据线都在，盒子也留着。电池健康度 91%，看视频能撑一整天。因为换了大屏平板所以出掉，可以当面验机。',
 1450.00, 2499.00, 3, 386, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 55 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 55 DAY), NOW(), 0),

(9002, 9007, 1, '索尼 WH-1000XM4 头戴降噪耳机',
 '去年双十一入的，通勤和图书馆都在用，降噪效果是真的好。耳罩没有脱皮，头梁也没有开裂。附赠原装收纳包和飞机转接头，充电线一起给。因为换了入耳式所以出，价格可以小刀，当面交易当场试听。',
 1080.00, 2299.00, 3, 274, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 52 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 52 DAY), NOW(), 0),

(9003, 9007, 1, '罗技 MX Master 3 无线鼠标 国行',
 '买来配笔记本做设计的，用了一年多，滚轮和侧键都灵敏，没有双击问题。附送 Type-C 充电线和原装收纳袋。手感很适合长期办公，只是我换成更小的鼠标了。支持蓝牙和接收器双模，接收器也在。',
 320.00, 699.00, 1, 168, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 48 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 48 DAY), NOW(), 0),

(9004, 9008, 1, '小米移动电源 20000mAh 双向快充',
 '大二买的，外出拍片时用来给相机和手机续电。容量实在，能给手机充四五次，双向快充 22.5W。外壳有两道浅划痕不影响使用，电量显示正常。原装数据线还在，出门带一个基本不用找插座。',
 65.00, 149.00, 1, 210, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 44 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 44 DAY), NOW(), 0),

(9005, 9008, 1, 'iPad 保护壳 + 钢化膜 全新未拆封',
 '买重了，多出来一套，包装都没拆。保护壳是带笔槽的磁吸款，钢化膜两片装。想了解的可以加微信细聊，宿舍楼下自提也行。型号是 10.2 英寸的，2021 款和 2020 款都通用，其他型号请先问清楚再拍。',
 25.00, 59.00, 0, 34, 0, 1, 1, '加微信', NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),

(9006, 9008, 1, '显卡 RTX 3060 8G 拆机卡 无保修',
 '朋友工作室升级换下来的，成色一般，风扇有点灰。核心没有修过，能点亮，跑分正常。没有原包装也没有发票，只保证能正常使用七天。适合预算紧、想凑一台过渡机的同学，介意成色的不要拍。',
 980.00, 2200.00, 4, 452, 0, 1, 0, NULL,
 '举报核实：商品违反平台规则（举报#9001）', 2,
 DATE_SUB(NOW(), INTERVAL 35 DAY), DATE_SUB(NOW(), INTERVAL 40 DAY), NOW(), 0),

-- ---------- 教材书籍（分类 2）：9007~9012 ----------
(9007, 9007, 2, '考研数学 张宇基础 30 讲 2026 版',
 '今年三月买的，只看了前六章，后面全新，没有笔记也没有划线。书角有点轻微压痕，书页干净。配套的习题册也在，一起给。考研换学校不考数二了所以出掉，可以约在图书馆门口当面看。',
 45.00, 168.00, 1, 512, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 45 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 45 DAY), NOW(), 0),

(9008, 9007, 2, '大学英语四级真题 2025 年 12 月版',
 '考完就闲置了，真题只做了前五套，后面全新，答案册完整。听力音频有二维码可以扫。里面有我自己整理的作文模板几张，不介意的话一起送你。四级已经过了，希望它帮到下一个同学。',
 18.00, 45.00, 1, 233, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 42 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 42 DAY), NOW(), 0),

(9009, 9008, 2, '《数据结构》严蔚敏 第 2 版 清华版',
 '专业课教材，跟着课程用了一学期，重点章节有铅笔勾画，可以用橡皮擦掉。书脊完好没有散页。配套的实验指导书也在，两本一起出。同校的同学可以直接来宿舍拿，不用邮寄。',
 22.00, 39.00, 1, 141, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 36 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 36 DAY), NOW(), 0),

(9010, 9008, 2, '考研英语 恋练有词 全新未写',
 '买了两本，这本一直没用，塑封拆了但内页全新，一个笔画都没有。附赠便携词汇卡片一盒。适合刚开始背单词的同学，按词根串讲的方式比硬背效率高。价格已经是入手价的三折，不再议价。',
 30.00, 69.00, 3, 305, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 33 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 33 DAY), NOW(), 0),

(9011, 9008, 2, '《管理学》罗宾斯 第 13 版 中文版',
 '专业课指定教材，很厚一本。前四章有荧光笔标记，后面干净。书页没有卷角，装订牢固。考试已经结束，留着占地方。同专业的学弟学妹拿走最合适，可以顺便把整理的重点提纲一起给你。',
 48.00, 128.00, 3, 176, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 30 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 30 DAY), NOW(), 0),

(9012, 9008, 2, '高等数学 同济第七版 上下册',
 '大一用的教材，两本一起出。上册有几页折角，下册基本全新。课后习题没有写答案，只在个别定理旁边画了线。价格是两本的总价，单买上册也可以但只便宜五块，建议一起拿。',
 26.00, 79.00, 3, 289, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 27 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 27 DAY), NOW(), 0),

-- ---------- 生活用品（分类 3）：9013~9018 ----------
(9013, 9009, 3, '宿舍折叠小书桌 实木色 60×40',
 '大一买的，在床上用了两年，桌腿的卡扣还是很紧，不晃。桌面有几处水杯留下的浅圈，擦不掉但不明显。收起来只有五厘米厚，塞在床缝里就行。毕业清宿舍，女生宿舍楼下自提，不邮寄。',
 35.00, 89.00, 3, 187, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 40 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 40 DAY), NOW(), 0),

(9014, 9009, 3, '亚都加湿器 3L 静音款',
 '北方冬天太干买的，开一夜宿舍也不吵，水箱 3 升能撑一整晚。出雾正常，滤芯上个月刚换过新的。外观没有磕碰，只是底部有一点点水垢印子，用白醋泡一下就能去掉。带原装电源适配器。',
 58.00, 199.00, 3, 165, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 37 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 37 DAY), NOW(), 0),

(9015, 9009, 3, '护眼台灯 三档调光 USB 供电',
 '宿舍床头用的，三档亮度，最暗那档晚上看手机不刺眼。灯臂可以弯折，USB 供电接充电宝也能用。用了一个半学期，灯珠没有坏点。搬宿舍带不走所以出，可以当场插电试亮度。',
 32.00, 89.00, 1, 243, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 33 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 33 DAY), NOW(), 0),

(9016, 9009, 3, '收纳箱三件套 可折叠 宿舍神器',
 '三个不同尺寸，能塞进床底和衣柜顶部，装换季衣服和杂物很够用。折叠起来只有一本书厚。买来用过一学期，没有破洞也没有异味。想看的可以私聊下单，约在女生宿舍楼下当面看货。',
 28.00, 79.00, 0, 89, 0, 1, 1, '私聊下单', NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 1 DAY), NOW(), 0),

(9017, 9009, 3, '全棉四件套 1.5 米床 用过两次',
 '买错尺寸了，我床是 1.2 米的，这套铺上去太大。只用过两次就洗干净收起来了，没有起球也没有褪色，被套拉链顺滑。颜色是浅豆绿，实物比图片更淡一点。介意二手的可以看看别的。',
 88.00, 268.00, 1, 132, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 30 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 30 DAY), NOW(), 0),

(9018, 9009, 3, '迷你小冰箱 22L 车载家用两用',
 '本来放宿舍冰饮料和面膜，制冷效果不错，运行声音很小。带 220V 插头和车载线两种供电。内胆干净没有异味，门封条完好。搬去没有电梯的六楼实在带不动了，只接受自提，可以帮忙搬到楼下。',
 220.00, 499.00, 3, 198, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 26 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 26 DAY), NOW(), 0),

-- ---------- 服饰鞋包（分类 4）：9019~9024 ----------
(9019, 9007, 4, '优衣库 U 系列卫衣 L 码 藏青',
 '去年秋天买的，穿过四五次，洗过两次，没有起球也没有变形。版型是宽松落肩那种，里面加件衬衫也塞得下。颜色是深藏青，比黑色显气色。因为瘦了十斤穿着太大所以出，衣柜里挂着的。',
 55.00, 199.00, 3, 121, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 38 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 38 DAY), NOW(), 0),

(9020, 9007, 4, 'Nike Air Force 1 白色 42 码',
 '正品，专柜买的，鞋盒和发票都还在。穿了大概十次，鞋面没有明显折痕，鞋底有一点正常磨损。洗过一次，白得还挺干净。因为脚伤了换成软底鞋，这双一直放着。介意二手的可以看细节图。',
 320.00, 799.00, 3, 268, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 35 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 35 DAY), NOW(), 0),

(9021, 9009, 4, '新秀丽双肩包 15.6 寸电脑隔层',
 '大一买的通勤包，装了两年电脑没有任何磨损，拉链顺滑，肩带没有开线。里面分层很多，电脑隔层有海绵保护。背板透气，夏天背不闷。换了大一码的包所以出，可以来看看实物再决定。',
 180.00, 599.00, 3, 154, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 24 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 24 DAY), NOW(), 0),

(9022, 9010, 4, '北面冲锋衣 男款 L 码 九成新',
 '去年爬山买的，防水层完好，袖口魔术贴粘性还很强。只穿过三次，没有破损也没有大面积污渍。内里是网眼那种，春秋单穿刚好。帽子可收纳。因为买大了一码，想换 M，所以先出掉这件。',
 380.00, 1299.00, 1, 226, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 30 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 30 DAY), NOW(), 0),

(9023, 9010, 4, '加绒加厚卫衣 全新带吊牌 可小刀',
 '网购的，尺码不合适退货又超时了，吊牌还在，一次都没穿过。里面是厚绒，冬天单穿够暖。颜色是浅灰，男生女生都能穿。诚心想要的可以加微信聊，价格能再商量，当面交易优先。',
 68.00, 159.00, 1, 96, 0, 1, 1, '加微信', NULL, 1,
 DATE_SUB(NOW(), INTERVAL 26 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 26 DAY), NOW(), 0),

(9024, 9010, 4, '回力帆布鞋 经典款 41 码',
 '买了没怎么穿，鞋头有点压脚所以一直闲置。鞋底几乎没有磨损，鞋垫是原装的。白色帆布有一点泛黄，可以自己用小苏打刷一下。价格已经很低了，主要是想清出鞋柜空间，不议价。',
 45.00, 119.00, 1, 88, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 22 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 22 DAY), NOW(), 0),

-- ---------- 运动户外（分类 5）：9025~9030 ----------
(9025, 9010, 5, '迪卡侬羽毛球拍 一对 送三只球',
 '体育课买的，打了一学期，拍框没有变形，线也还没松。握把的胶带换过一次新的。送三只训练球，其中一只用过。适合刚入门的同学，两个人凑一对正好。价格是一对的总价。',
 60.00, 159.00, 3, 143, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 20 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 20 DAY), NOW(), 0),

(9026, 9010, 5, 'TPE 瑜伽垫 加厚 8mm 带背带',
 '跟着视频练了两个月，后来改去健身房就不用了。8 毫米厚度，跪姿动作膝盖不疼。表面防滑纹路还在，用湿布擦过，没有异味。附带收纳背带。卷起来不算重，可以拎着来交接。',
 38.00, 99.00, 3, 112, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 18 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 18 DAY), NOW(), 0),

(9027, 9010, 5, '捷安特 ATX 27 速山地车 M 码',
 '骑了一年半，平时上下课代步，链条上个月刚换新的，刹车皮也换过。车架有两处掉漆不影响骑。27 速变速都正常，前后避震没有漏油。带车锁和打气筒一起给。只接受自提，可以约在车棚看车。',
 780.00, 2199.00, 1, 421, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 16 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 16 DAY), NOW(), 0),

(9028, 9010, 5, '可调节哑铃 20kg 一对',
 '宿舍练手臂用的，两边各 10 公斤，可以拆成四个 5 公斤的片。包胶的，掉在地上声音不大。手柄防滑纹路磨损了一点。因为搬去校外住，东西太多带不走，只能自提，五楼没有电梯请提前说。',
 130.00, 399.00, 2, 178, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 16 DAY), DATE_SUB(NOW(), INTERVAL 16 DAY), NOW(), 0),

(9029, 9011, 5, '帐篷 2-3 人 自动速开 防雨',
 '露营买的第一顶帐篷，自动速开那种，一个人两分钟能撑起来。防雨 3000mm，下小雨没问题。只用过两次，没有破洞，帐杆完好。带收纳袋和地钉。因为升级了四人帐所以出，价格可以商量。',
 168.00, 459.00, 1, 205, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 28 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 28 DAY), NOW(), 0),

(9030, 9011, 5, '斯伯丁篮球 7 号 室内外通用',
 '大二买的，主要在室外球场打，表皮磨损但不影响手感。气密性正常，打足气放一周不会软。球面有几道黑色划痕。已经买了新的比赛用球，这只好价转给常打球的同学，送一个球袋。',
 68.00, 159.00, 3, 96, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 25 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 25 DAY), NOW(), 0),

-- ---------- 交通工具（分类 6）：9031~9035 ----------
(9031, 9011, 6, '折叠电动车 续航 40km 带牌照',
 '去年买的通勤车，电池换过一次新的，实测续航还有 38 到 40 公里。折叠起来能塞进后备箱。刹车灵敏，灯光正常，牌照和购车凭证都在。因为毕业要离开这个城市所以出，价格含充电器，可当面试骑。',
 890.00, 2599.00, 3, 476, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 21 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 21 DAY), NOW(), 0),

(9032, 9011, 6, '九成新山地车 送车锁 已保养',
 '骑了不到一年，刚做过保养，链条上过油，变速调过。车架是磨砂黑，没有明显划痕。前后刹车都很灵。带一把 U 型锁和一个水壶架。宿舍楼下就能看车，试骑满意再付款，不接受邮寄。',
 380.00, 1299.00, 3, 342, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 19 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 19 DAY), NOW(), 0),

(9033, 9011, 6, '小米电动滑板车 续航 20km',
 '上下课代步用的，续航实测 18 到 20 公里，充一次能用三四天。轮胎是实心免充气的，不用怕扎。刹车灯和前后灯都正常。折叠扣有点紧，用点力就行。带原装充电器，可以约在操场边试骑。',
 520.00, 1599.00, 1, 289, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 12 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 12 DAY), NOW(), 0),

(9034, 9011, 6, '城市通勤自行车 单速 26 寸',
 '很轻的一辆单速车，女生也能轻松搬上楼梯。链条和飞轮都是新换的，骑起来没有异响。车筐和挡泥板齐全。适合校内短途代步，不适合爬坡。价格便宜，主要是想把车棚位置腾出来。',
 240.00, 699.00, 1, 151, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 9 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 9 DAY), NOW(), 0),

(9035, 9012, 6, '电动车充电器 48V 通用 原装',
 '换车之后多出来的，接口是常见的品字头，48V 12AH 和 20AH 都能用。风扇正常，充电时指示灯会从红变绿。线材没有破损。放在宿舍也是占地方，便宜出，可以现场插上试一下。',
 35.00, 89.00, 3, 78, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 20 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 20 DAY), NOW(), 0),

-- ---------- 其他（分类 7）：9036~9040 ----------
(9036, 9012, 7, '民谣吉他 40 寸 送琴包',
 '大二开始学的，弹了一年多，琴颈没有变形，弦距正常不打品。面板有几道细小划痕。送琴包、变调夹、备用弦一套。因为准备考级换全单琴所以出。可以来宿舍试弹，满意再拿走。',
 320.00, 899.00, 3, 234, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 17 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 17 DAY), NOW(), 0),

(9037, 9012, 7, '化妆刷套装 12 支 全新未拆',
 '朋友送的，我平时不化妆一直放着，塑封都没拆。刷毛是纤维的，不掉毛。带一个收纳包。整套十二支，眼影刷腮红刷都有。放着也是浪费，低价转给需要的同学，可以当面拆开检查。',
 25.00, 79.00, 2, 67, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY), NOW(), 0),

(9038, 9012, 7, '小米吹风机 负离子 2200W',
 '用了半年，风力很足，负离子功能正常，冷风档也在。机身没有磕碰，进风口的滤网定期清过。带一个集风嘴。因为剪短发了用不上所以出。价格已经很低，可以在宿舍楼下插电试机。',
 79.00, 199.00, 1, 163, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 10 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 10 DAY), NOW(), 0),

(9039, 9012, 7, '行李箱 24 寸 万向轮 密码锁',
 '大一入学买的，用过三次，一次是开学一次是回家一次是出去玩。外壳有几道托运留下的浅痕，拉链和密码锁都正常，四个万向轮转起来顺滑。内衬干净。因为买了更大的箱子所以出，可自提。',
 150.00, 459.00, 1, 187, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 7 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 7 DAY), NOW(), 0),

(9040, 9012, 7, '全自动雨伞 抗风 双人加大',
 '买了两把，一把放宿舍一把放教室，结果教室那把一直没用过。八骨加固，风大不会翻。一键开收，伞面是加大的，两个人挤一挤能遮住。全新状态，吊牌还在。价格便宜，不议价，自取优先。',
 28.00, 79.00, 1, 54, 0, 1, 0, NULL, NULL, 1,
 DATE_SUB(NOW(), INTERVAL 4 DAY) + INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0);


-- =============================================================
-- 6. 商品图片（每件 2 张：主图按分类配色，第二张错开一张，详情页才有得滑）
--    图片是 backend/uploads/demo/cover-1~6.png，6 种配色，无版权问题
-- =============================================================
INSERT INTO `product_image` (`id`, `product_id`, `image_url`, `sort`, `create_time`, `deleted`)
SELECT 9000 + p.`id` - 9000, p.`id`,
       CONCAT('/uploads/demo/cover-', ((p.`category_id` - 1) % 6) + 1, '.png'),
       1, p.`create_time`, 0
FROM `product` p WHERE p.`id` BETWEEN 9001 AND 9040;

INSERT INTO `product_image` (`id`, `product_id`, `image_url`, `sort`, `create_time`, `deleted`)
SELECT 9100 + p.`id` - 9000, p.`id`,
       CONCAT('/uploads/demo/cover-', ((p.`category_id` + 1) % 6) + 1, '.png'),
       2, p.`create_time`, 0
FROM `product` p WHERE p.`id` BETWEEN 9001 AND 9040;


-- =============================================================
-- 7. 订单 26 笔
--    5 待付款 / 3 待发货 / 4 待收货 / 9 已完成 / 2 退款中 / 3 已取消
--    说明：需求写的是「25 笔、8 笔已完成」，但评价要求 18 条且「只评已完成订单」，
--          而一个订单最多两条评价（买家评卖家 + 卖家评买家，uk_order_id_type 唯一），
--          8 × 2 = 16 < 18。因此把已完成做到 9 笔（9 × 2 = 18），总数 26 笔。
--          这样评价数量与星级分布都能严格满足，且不会出现「一单三条评价」的假数据。
--    买家/卖家都从 12 个演示用户里选，且保证买家不是卖家本人。
-- =============================================================
INSERT INTO `trade_order`
(`id`, `order_no`, `buyer_id`, `seller_id`, `product_id`, `product_snapshot`, `price`, `status`,
 `refund_from_status`, `buyer_reviewed`, `seller_reviewed`, `address_snapshot`,
 `pay_deadline`, `payment_time`, `ship_time`, `finish_time`, `cancel_time`, `refund_time`,
 `cancel_reason`, `refund_reason`, `reject_refund_reason`, `create_time`, `update_time`, `deleted`)
VALUES
-- ---------- 待付款 5 笔（商品不占用，仍在售）----------
-- 注意：pay_deadline 刻意放到 NOW() + 3 天，而不是「下单 + 30 分钟」。
-- 后端有订单超时任务（OrderTimeoutTask，默认 5 分钟一扫），真实规则下这些
-- 3 天前的待付款订单会在启动后几分钟内被自动取消，演示数据当场少一个状态。
-- 演示数据要的是「能稳定看到 5 笔待付款」，因此这里放宽支付时限。
(9001, 'TMP9001', 9001, 9007, 9003, '{}', 320.00, 0, NULL, 0, 0, '{}',
 DATE_ADD(NOW(), INTERVAL 3 DAY), NULL, NULL, NULL, NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(9002, 'TMP9002', 9002, 9007, 9007, '{}', 45.00, 0, NULL, 0, 0, '{}',
 DATE_ADD(NOW(), INTERVAL 3 DAY), NULL, NULL, NULL, NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW(), 0),
(9003, 'TMP9003', 9003, 9010, 9022, '{}', 380.00, 0, NULL, 0, 0, '{}',
 DATE_ADD(NOW(), INTERVAL 3 DAY), NULL, NULL, NULL, NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),
(9004, 'TMP9004', 9004, 9011, 9033, '{}', 520.00, 0, NULL, 0, 0, '{}',
 DATE_ADD(NOW(), INTERVAL 3 DAY), NULL, NULL, NULL, NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 20 HOUR), NOW(), 0),
(9005, 'TMP9005', 9005, 9012, 9038, '{}', 79.00, 0, NULL, 0, 0, '{}',
 DATE_ADD(NOW(), INTERVAL 3 DAY), NULL, NULL, NULL, NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 9 HOUR), NOW(), 0),

-- ---------- 待发货 3 笔（已付款，商品置为已售出）----------
(9006, 'TMP9006', 9001, 9007, 9001, '{}', 1450.00, 1, NULL, 0, 0, '{}',
 DATE_SUB(NOW(), INTERVAL 5 DAY) + INTERVAL 30 MINUTE,
 DATE_SUB(NOW(), INTERVAL 5 DAY) + INTERVAL 8 MINUTE, NULL, NULL, NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), NOW(), 0),
(9007, 'TMP9007', 9003, 9008, 9010, '{}', 30.00, 1, NULL, 0, 0, '{}',
 DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 30 MINUTE,
 DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 12 MINUTE, NULL, NULL, NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), NOW(), 0),
(9008, 'TMP9008', 9008, 9007, 9019, '{}', 55.00, 1, NULL, 0, 0, '{}',
 DATE_SUB(NOW(), INTERVAL 2 DAY) + INTERVAL 30 MINUTE,
 DATE_SUB(NOW(), INTERVAL 2 DAY) + INTERVAL 6 MINUTE, NULL, NULL, NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), NOW(), 0),

-- ---------- 待收货 4 笔（已发货，未确认收货）----------
(9009, 'TMP9009', 9002, 9007, 9002, '{}', 1080.00, 2, NULL, 0, 0, '{}',
 DATE_SUB(NOW(), INTERVAL 9 DAY) + INTERVAL 30 MINUTE,
 DATE_SUB(NOW(), INTERVAL 9 DAY) + INTERVAL 7 MINUTE,
 DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 9 DAY), NOW(), 0),
(9010, 'TMP9010', 9004, 9008, 9011, '{}', 48.00, 2, NULL, 0, 0, '{}',
 DATE_SUB(NOW(), INTERVAL 8 DAY) + INTERVAL 30 MINUTE,
 DATE_SUB(NOW(), INTERVAL 8 DAY) + INTERVAL 11 MINUTE,
 DATE_SUB(NOW(), INTERVAL 7 DAY), NULL, NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 8 DAY), NOW(), 0),
(9011, 'TMP9011', 9006, 9007, 9020, '{}', 320.00, 2, NULL, 0, 0, '{}',
 DATE_SUB(NOW(), INTERVAL 7 DAY) + INTERVAL 30 MINUTE,
 DATE_SUB(NOW(), INTERVAL 7 DAY) + INTERVAL 9 MINUTE,
 DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 7 DAY), NOW(), 0),
(9012, 'TMP9012', 9009, 9010, 9025, '{}', 60.00, 2, NULL, 0, 0, '{}',
 DATE_SUB(NOW(), INTERVAL 6 DAY) + INTERVAL 30 MINUTE,
 DATE_SUB(NOW(), INTERVAL 6 DAY) + INTERVAL 5 MINUTE,
 DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), 0),

-- ---------- 已完成 9 笔（有发货时间、收货时间，且都已被双方评价）----------
(9013, 'TMP9013', 9001, 9008, 9012, '{}', 26.00, 3, NULL, 1, 1, '{}',
 DATE_SUB(NOW(), INTERVAL 24 DAY) + INTERVAL 30 MINUTE,
 DATE_SUB(NOW(), INTERVAL 24 DAY) + INTERVAL 6 MINUTE,
 DATE_SUB(NOW(), INTERVAL 23 DAY), DATE_SUB(NOW(), INTERVAL 21 DAY), NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 24 DAY), NOW(), 0),
(9014, 'TMP9014', 9002, 9009, 9013, '{}', 35.00, 3, NULL, 1, 1, '{}',
 DATE_SUB(NOW(), INTERVAL 22 DAY) + INTERVAL 30 MINUTE,
 DATE_SUB(NOW(), INTERVAL 22 DAY) + INTERVAL 9 MINUTE,
 DATE_SUB(NOW(), INTERVAL 21 DAY), DATE_SUB(NOW(), INTERVAL 19 DAY), NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 22 DAY), NOW(), 0),
(9015, 'TMP9015', 9003, 9009, 9014, '{}', 58.00, 3, NULL, 1, 1, '{}',
 DATE_SUB(NOW(), INTERVAL 21 DAY) + INTERVAL 30 MINUTE,
 DATE_SUB(NOW(), INTERVAL 21 DAY) + INTERVAL 4 MINUTE,
 DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 21 DAY), NOW(), 0),
(9016, 'TMP9016', 9004, 9009, 9018, '{}', 220.00, 3, NULL, 1, 1, '{}',
 DATE_SUB(NOW(), INTERVAL 19 DAY) + INTERVAL 30 MINUTE,
 DATE_SUB(NOW(), INTERVAL 19 DAY) + INTERVAL 13 MINUTE,
 DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 16 DAY), NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 19 DAY), NOW(), 0),
(9017, 'TMP9017', 9005, 9009, 9021, '{}', 180.00, 3, NULL, 1, 1, '{}',
 DATE_SUB(NOW(), INTERVAL 17 DAY) + INTERVAL 30 MINUTE,
 DATE_SUB(NOW(), INTERVAL 17 DAY) + INTERVAL 8 MINUTE,
 DATE_SUB(NOW(), INTERVAL 16 DAY), DATE_SUB(NOW(), INTERVAL 14 DAY), NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 17 DAY), NOW(), 0),
(9018, 'TMP9018', 9006, 9010, 9026, '{}', 38.00, 3, NULL, 1, 1, '{}',
 DATE_SUB(NOW(), INTERVAL 15 DAY) + INTERVAL 30 MINUTE,
 DATE_SUB(NOW(), INTERVAL 15 DAY) + INTERVAL 10 MINUTE,
 DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 15 DAY), NOW(), 0),
(9019, 'TMP9019', 9007, 9011, 9030, '{}', 68.00, 3, NULL, 1, 1, '{}',
 DATE_SUB(NOW(), INTERVAL 13 DAY) + INTERVAL 30 MINUTE,
 DATE_SUB(NOW(), INTERVAL 13 DAY) + INTERVAL 5 MINUTE,
 DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 13 DAY), NOW(), 0),
(9020, 'TMP9020', 9010, 9011, 9031, '{}', 890.00, 3, NULL, 1, 1, '{}',
 DATE_SUB(NOW(), INTERVAL 11 DAY) + INTERVAL 30 MINUTE,
 DATE_SUB(NOW(), INTERVAL 11 DAY) + INTERVAL 12 MINUTE,
 DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 11 DAY), NOW(), 0),
(9021, 'TMP9021', 9012, 9011, 9032, '{}', 380.00, 3, NULL, 1, 1, '{}',
 DATE_SUB(NOW(), INTERVAL 10 DAY) + INTERVAL 30 MINUTE,
 DATE_SUB(NOW(), INTERVAL 10 DAY) + INTERVAL 7 MINUTE,
 DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), NULL, NULL,
 NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 10 DAY), NOW(), 0),

-- ---------- 退款中 2 笔（退款申请待卖家处理，商品仍被占用）----------
(9022, 'TMP9022', 9002, 9012, 9035, '{}', 35.00, 5, 2, 0, 0, '{}',
 DATE_SUB(NOW(), INTERVAL 12 DAY) + INTERVAL 30 MINUTE,
 DATE_SUB(NOW(), INTERVAL 12 DAY) + INTERVAL 6 MINUTE,
 DATE_SUB(NOW(), INTERVAL 11 DAY), NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 6 DAY),
 NULL, '收到的充电器插头和我的车不匹配，风扇也不转，申请退货退款', NULL,
 DATE_SUB(NOW(), INTERVAL 12 DAY), NOW(), 0),
(9023, 'TMP9023', 9003, 9012, 9036, '{}', 320.00, 5, 2, 0, 0, '{}',
 DATE_SUB(NOW(), INTERVAL 14 DAY) + INTERVAL 30 MINUTE,
 DATE_SUB(NOW(), INTERVAL 14 DAY) + INTERVAL 9 MINUTE,
 DATE_SUB(NOW(), INTERVAL 13 DAY), NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 5 DAY),
 NULL, '吉他琴颈有一道裂纹，发货前没有说明，希望退货', NULL,
 DATE_SUB(NOW(), INTERVAL 14 DAY), NOW(), 0),

-- ---------- 已取消 3 笔（商品放回在售）----------
(9024, 'TMP9024', 9004, 9008, 9004, '{}', 65.00, 4, NULL, 0, 0, '{}',
 DATE_SUB(NOW(), INTERVAL 16 DAY) + INTERVAL 30 MINUTE, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 16 DAY) + INTERVAL 2 HOUR, NULL,
 '超过 30 分钟未付款，系统自动取消', NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 16 DAY), NOW(), 0),
(9025, 'TMP9025', 9005, 9010, 9023, '{}', 68.00, 4, NULL, 0, 0, '{}',
 DATE_SUB(NOW(), INTERVAL 18 DAY) + INTERVAL 30 MINUTE, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 18 DAY) + INTERVAL 90 MINUTE, NULL,
 '临时不需要了，买家主动取消', NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 18 DAY), NOW(), 0),
(9026, 'TMP9026', 9006, 9009, 9015, '{}', 32.00, 4, NULL, 0, 0, '{}',
 DATE_SUB(NOW(), INTERVAL 20 DAY) + INTERVAL 30 MINUTE, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 20 DAY) + INTERVAL 2 HOUR, NULL,
 '超过 30 分钟未付款，系统自动取消', NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 20 DAY), NOW(), 0);


-- 订单号按「下单时间 + 序号」生成，与 create_time 保持一致（占位值 TMP 只是插入时的临时值）
UPDATE `trade_order`
SET `order_no` = CONCAT(DATE_FORMAT(`create_time`, '%Y%m%d%H%i%s'), LPAD(`id` - 9000, 3, '0'))
WHERE `id` BETWEEN 9001 AND 9026;

-- 商品快照：下单瞬间的商品信息，之后卖家改价/改标题都不影响历史订单
UPDATE `trade_order` o
JOIN `product` p ON p.`id` = o.`product_id`
JOIN `category` c ON c.`id` = p.`category_id`
SET o.`product_snapshot` = JSON_OBJECT(
    'productId', p.`id`,
    'title', p.`title`,
    'cover', (SELECT pi.`image_url` FROM `product_image` pi
              WHERE pi.`product_id` = p.`id` AND pi.`sort` = 1 LIMIT 1),
    'price', p.`price`,
    'originalPrice', p.`original_price`,
    'categoryName', c.`name`,
    'description', LEFT(p.`description`, 200),
    'isVerifiedSeller', p.`is_verified_seller` = 1)
WHERE o.`id` BETWEEN 9001 AND 9026;

-- 收货地址快照：同上，用户之后改地址不影响历史订单
UPDATE `trade_order` o
JOIN `address` a ON a.`user_id` = o.`buyer_id`
SET o.`address_snapshot` = JSON_OBJECT(
    'id', a.`id`,
    'receiverName', a.`receiver_name`,
    'phone', a.`phone`,
    'address', a.`address`,
    'isDefault', a.`is_default` = 1)
WHERE o.`id` BETWEEN 9001 AND 9026;


-- =============================================================
-- 8. 订单日志（时间线由订单状态反推，保证与订单状态一致）
--    用 INSERT ... SELECT 而不是逐条手写：日志条数与顺序不可能和订单状态对不上
-- =============================================================
-- 8.1 下单
INSERT INTO `order_log` (`order_id`, `operator_id`, `operator_role`, `action`,
 `from_status`, `to_status`, `remark`, `create_time`)
SELECT `id`, `buyer_id`, 'buyer', 'create', NULL, 0,
       CONCAT('下单成功，成交价 ', CAST(`price` AS CHAR), ' 元'), `create_time`
FROM `trade_order` WHERE `id` BETWEEN 9001 AND 9026;

-- 8.2 支付
INSERT INTO `order_log` (`order_id`, `operator_id`, `operator_role`, `action`,
 `from_status`, `to_status`, `remark`, `create_time`)
SELECT `id`, `buyer_id`, 'buyer', 'pay', 0, 1, '支付成功（模拟支付）', `payment_time`
FROM `trade_order` WHERE `id` BETWEEN 9001 AND 9026 AND `payment_time` IS NOT NULL;

-- 8.3 发货
INSERT INTO `order_log` (`order_id`, `operator_id`, `operator_role`, `action`,
 `from_status`, `to_status`, `remark`, `create_time`)
SELECT `id`, `seller_id`, 'seller', 'ship', 1, 2, '卖家已发货，约在宿舍楼下自提', `ship_time`
FROM `trade_order` WHERE `id` BETWEEN 9001 AND 9026 AND `ship_time` IS NOT NULL;

-- 8.4 确认收货
INSERT INTO `order_log` (`order_id`, `operator_id`, `operator_role`, `action`,
 `from_status`, `to_status`, `remark`, `create_time`)
SELECT `id`, `buyer_id`, 'buyer', 'receive', 2, 3, '买家已确认收货，交易完成', `finish_time`
FROM `trade_order` WHERE `id` BETWEEN 9001 AND 9026 AND `finish_time` IS NOT NULL;

-- 8.5 申请退款
INSERT INTO `order_log` (`order_id`, `operator_id`, `operator_role`, `action`,
 `from_status`, `to_status`, `remark`, `create_time`)
SELECT `id`, `buyer_id`, 'buyer', 'apply_refund', `refund_from_status`, 5,
       COALESCE(`refund_reason`, '买家申请退款'), `refund_time`
FROM `trade_order` WHERE `id` BETWEEN 9001 AND 9026 AND `status` = 5;

-- 8.6 取消
INSERT INTO `order_log` (`order_id`, `operator_id`, `operator_role`, `action`,
 `from_status`, `to_status`, `remark`, `create_time`)
SELECT `id`, `buyer_id`, 'buyer', 'cancel', 0, 4,
       COALESCE(`cancel_reason`, '订单已取消'), `cancel_time`
FROM `trade_order` WHERE `id` BETWEEN 9001 AND 9026 AND `status` = 4;


-- =============================================================
-- 9. 评价 18 条（只评已完成的 9 笔订单，每笔一买一卖两条）
--    星级分布：5 星 10 条 / 4 星 4 条 / 3 星 2 条 / 2 星 1 条 / 1 星 1 条
--    3 条带追评（间隔 3-7 天）、2 条匿名、1 条含警告级敏感词「骗子」
--    说明：需求里的「1 条评价命中敏感词被拦截」在库里不可能存在——
--          拦截级敏感词在提交时就被拒绝，不会落库。这里改为演示「警告级命中：
--          照常展示但写入检测日志」（评价 9015 含「骗子」），效果更贴近真实机制。
-- =============================================================
INSERT INTO `review`
(`id`, `order_id`, `order_no`, `product_id`, `reviewer_id`, `reviewee_id`, `review_type`, `stars`,
 `desc_stars`, `ship_stars`, `attitude_stars`, `pay_stars`, `content`, `images`, `is_anonymous`,
 `reply_content`, `reply_time`, `follow_content`, `follow_images`, `follow_time`,
 `create_time`, `update_time`, `deleted`)
VALUES
-- 订单 9013：买家 9001 评卖家 9008（教材）
(9001, 9013, '', 9012, 9001, 9008, 1, 5, 5, 5, 5, NULL,
 '书比照片里还干净，上下册都在，卖家还多送了一张自己整理的重点提纲，很仔细。交易全程没催过我，满意。',
 NULL, 0, NULL, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 20 DAY), NOW(), 0),
(9002, 9013, '', 9012, 9008, 9001, 2, 5, NULL, NULL, 5, 5,
 '买家很爽快，约好时间就来了，付款也及时，全程没有扯皮，欢迎下次再来。',
 NULL, 0, NULL, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 20 DAY), NOW(), 0),

-- 订单 9014：买家 9002 评卖家 9009（折叠书桌）
(9003, 9014, '', 9013, 9002, 9009, 1, 5, 5, 5, 5, NULL,
 '桌子挺稳的，桌面那点水印确实不明显，卖家提前就说明了这点很诚实。女生宿舍楼下自提，帮忙搬到门口。',
 NULL, 0, NULL, NULL,
 '用了半个月来追评：螺丝没有松动，桌面也没变形，比我上一张折叠桌结实多了。',
 NULL, DATE_SUB(NOW(), INTERVAL 13 DAY),
 DATE_SUB(NOW(), INTERVAL 18 DAY), NOW(), 0),
(9004, 9014, '', 9013, 9009, 9002, 2, 5, NULL, NULL, 5, 5,
 '学弟人很好，自己带上楼了，还帮忙检查了一遍螺丝，非常省心的一次交易。',
 NULL, 0, NULL, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 18 DAY), NOW(), 0),

-- 订单 9015：买家 9003 评卖家 9009（加湿器）
(9005, 9015, '', 9014, 9003, 9009, 1, 5, 5, 5, 5, NULL,
 '加湿器开一夜确实很安静，滤芯是新的，出雾量也够，比宿舍原来那台老机器好太多了。',
 NULL, 0, NULL, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 17 DAY), NOW(), 0),
(9006, 9015, '', 9014, 9009, 9003, 2, 5, NULL, NULL, 5, 5,
 '买家提前问清了滤芯情况，沟通清楚，交易过程很顺利，好评。',
 NULL, 0, NULL, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 17 DAY), NOW(), 0),

-- 订单 9016：买家 9004 评卖家 9009（小冰箱）
(9007, 9016, '', 9018, 9004, 9009, 1, 4, 4, 4, 5, NULL,
 '冰箱制冷没问题，成色也符合描述，就是实物比描述里说的要重一些，搬到楼下花了点力气。',
 NULL, 0, NULL, NULL,
 '补充一下：用了一周，制冷依旧稳定，内胆没有异味，整体还是值这个价的。',
 NULL, DATE_SUB(NOW(), INTERVAL 12 DAY),
 DATE_SUB(NOW(), INTERVAL 15 DAY), NOW(), 0),
(9008, 9016, '', 9018, 9009, 9004, 2, 5, NULL, NULL, 5, 5,
 '买家自提时带了推车，很守时，也没有因为搬运麻烦而压价，靠谱。',
 NULL, 0, NULL, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 15 DAY), NOW(), 0),

-- 订单 9017：买家 9005 评卖家 9009（双肩包），匿名
(9009, 9017, '', 9021, 9005, 9009, 1, 5, 5, 5, 5, NULL,
 '包的分层比图片里看得还细致，电脑隔层的海绵很厚，通勤用完全够了，拉链也很顺。',
 NULL, 1, NULL, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 13 DAY), NOW(), 0),
(9010, 9017, '', 9021, 9009, 9005, 2, 5, NULL, NULL, 5, 5,
 '买家问得很细，确认了尺码和磨损情况才下单，沟通效率高，好评。',
 NULL, 0, NULL, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 13 DAY), NOW(), 0),

-- 订单 9018：买家 9006 评卖家 9010（瑜伽垫）
(9011, 9018, '', 9026, 9006, 9010, 1, 3, 3, 4, 4, NULL,
 '垫子本身还行，但边缘有一处明显的压痕，卖家照片里没有拍到，收到时稍微有点落差。',
 NULL, 0, NULL, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 11 DAY), NOW(), 0),
(9012, 9018, '', 9026, 9010, 9006, 2, 4, NULL, NULL, 5, 4,
 '买家付款很快，也没有为难人，就是问题比较多，可以理解，祝使用愉快。',
 NULL, 0, NULL, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 11 DAY), NOW(), 0),

-- 订单 9019：买家 9007 评卖家 9011（篮球）
(9013, 9019, '', 9030, 9007, 9011, 1, 5, 5, 5, 5, NULL,
 '球的手感不错，气也足，卖家还送了一个球袋，比在店里买划算多了，下次还来。',
 NULL, 0, NULL, NULL,
 '打了几场再来追评：表皮没有起皮，气密性也稳，室外水泥地打完全没问题。',
 NULL, DATE_SUB(NOW(), INTERVAL 2 DAY),
 DATE_SUB(NOW(), INTERVAL 9 DAY), NOW(), 0),
(9014, 9019, '', 9030, 9011, 9007, 2, 4, NULL, NULL, 4, 5,
 '买家爽快，就是约的时间改了两次，其他都挺好的。',
 NULL, 0, NULL, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 9 DAY), NOW(), 0),

-- 订单 9020：买家 9010 评卖家 9011（折叠电动车）—— 2 星，含警告级敏感词「骗子」
(9015, 9020, '', 9031, 9010, 9011, 1, 2, 2, 3, 4, NULL,
 '续航和描述差得有点多，实际只能跑二十多公里，电池应该是老化了。虽然卖家不是骗子，但希望以后描述写准确一点。',
 NULL, 0, NULL, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 7 DAY), NOW(), 0),
(9016, 9020, '', 9031, 9011, 9010, 2, 4, NULL, NULL, 4, 4,
 '买家试骑之后又考虑了一天，最后还是要了，付款及时，就是沟通节奏慢一点。',
 NULL, 0, NULL, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 7 DAY), NOW(), 0),

-- 订单 9021：买家 9012 评卖家 9011（山地车）—— 1 星差评 + 卖家回复
(9017, 9021, '', 9032, 9012, 9011, 1, 1, 1, 2, 2, NULL,
 '车到手发现后刹车是松的，链条也有异响，和说的刚做过保养完全对不上，很失望。',
 NULL, 0,
 '抱歉让你有不好的体验。刹车线是运输中松的，已经和你说过可以随时过来调，我这边工具都留着；链条异响是缺油，加一次油就能好。随时联系我处理。',
 DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), 0),
(9018, 9021, '', 9032, 9011, 9012, 2, 3, NULL, NULL, 3, 3,
 '买家收到后没有先沟通就直接给了差评，其实刹车调一下就好，有点委屈。',
 NULL, 1, NULL, NULL, NULL, NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 6 DAY), NOW(), 0);

-- 评价里的订单号与订单表保持一致（评价列表要显示订单号，手写容易和订单表对不上）
UPDATE `review` r JOIN `trade_order` o ON o.`id` = r.`order_id`
SET r.`order_no` = o.`order_no`, r.`update_time` = r.`create_time`
WHERE r.`id` BETWEEN 9001 AND 9018;

-- 被评价过的订单标记为已评价（详情页才会显示「已评价」而不是还挂着评价按钮）
UPDATE `trade_order` SET `buyer_reviewed` = 1, `seller_reviewed` = 1 WHERE `id` BETWEEN 9013 AND 9021;


-- =============================================================
-- 10. 收藏与关注（每个用户收藏 3-8 件，用户之间形成关注网络）
--     收藏数不写死：最后统一按 favorite 表真实行数回填 product.favorite_count，
--     避免出现「显示 45 人收藏、点进去只有 3 条」的对不上
-- =============================================================
INSERT INTO `favorite` (`id`, `user_id`, `product_id`, `create_time`) VALUES
(9001, 9001, 9007, DATE_SUB(NOW(), INTERVAL 30 DAY)),
(9002, 9001, 9015, DATE_SUB(NOW(), INTERVAL 26 DAY)),
(9003, 9001, 9027, DATE_SUB(NOW(), INTERVAL 15 DAY)),
(9004, 9001, 9034, DATE_SUB(NOW(), INTERVAL 9 DAY)),

(9005, 9002, 9003, DATE_SUB(NOW(), INTERVAL 40 DAY)),
(9006, 9002, 9015, DATE_SUB(NOW(), INTERVAL 31 DAY)),
(9007, 9002, 9022, DATE_SUB(NOW(), INTERVAL 28 DAY)),
(9008, 9002, 9029, DATE_SUB(NOW(), INTERVAL 20 DAY)),
(9009, 9002, 9038, DATE_SUB(NOW(), INTERVAL 8 DAY)),

(9010, 9003, 9007, DATE_SUB(NOW(), INTERVAL 44 DAY)),
(9011, 9003, 9008, DATE_SUB(NOW(), INTERVAL 41 DAY)),
(9012, 9003, 9017, DATE_SUB(NOW(), INTERVAL 29 DAY)),
(9013, 9003, 9033, DATE_SUB(NOW(), INTERVAL 11 DAY)),

(9014, 9004, 9003, DATE_SUB(NOW(), INTERVAL 34 DAY)),
(9015, 9004, 9022, DATE_SUB(NOW(), INTERVAL 27 DAY)),
(9016, 9004, 9027, DATE_SUB(NOW(), INTERVAL 14 DAY)),
(9017, 9004, 9034, DATE_SUB(NOW(), INTERVAL 8 DAY)),
(9018, 9004, 9038, DATE_SUB(NOW(), INTERVAL 6 DAY)),
(9019, 9004, 9039, DATE_SUB(NOW(), INTERVAL 5 DAY)),

(9020, 9005, 9003, DATE_SUB(NOW(), INTERVAL 36 DAY)),
(9021, 9005, 9007, DATE_SUB(NOW(), INTERVAL 33 DAY)),
(9022, 9005, 9015, DATE_SUB(NOW(), INTERVAL 25 DAY)),
(9023, 9005, 9023, DATE_SUB(NOW(), INTERVAL 19 DAY)),
(9024, 9005, 9033, DATE_SUB(NOW(), INTERVAL 10 DAY)),

(9025, 9006, 9008, DATE_SUB(NOW(), INTERVAL 22 DAY)),
(9026, 9006, 9015, DATE_SUB(NOW(), INTERVAL 18 DAY)),
(9027, 9006, 9022, DATE_SUB(NOW(), INTERVAL 13 DAY)),
(9028, 9006, 9038, DATE_SUB(NOW(), INTERVAL 4 DAY)),

(9029, 9007, 9015, DATE_SUB(NOW(), INTERVAL 32 DAY)),
(9030, 9007, 9017, DATE_SUB(NOW(), INTERVAL 27 DAY)),
(9031, 9007, 9022, DATE_SUB(NOW(), INTERVAL 24 DAY)),
(9032, 9007, 9029, DATE_SUB(NOW(), INTERVAL 17 DAY)),
(9033, 9007, 9033, DATE_SUB(NOW(), INTERVAL 12 DAY)),
(9034, 9007, 9039, DATE_SUB(NOW(), INTERVAL 7 DAY)),

(9035, 9008, 9003, DATE_SUB(NOW(), INTERVAL 38 DAY)),
(9036, 9008, 9015, DATE_SUB(NOW(), INTERVAL 30 DAY)),
(9037, 9008, 9022, DATE_SUB(NOW(), INTERVAL 23 DAY)),
(9038, 9008, 9027, DATE_SUB(NOW(), INTERVAL 13 DAY)),
(9039, 9008, 9033, DATE_SUB(NOW(), INTERVAL 6 DAY)),

(9040, 9009, 9003, DATE_SUB(NOW(), INTERVAL 35 DAY)),
(9041, 9009, 9007, DATE_SUB(NOW(), INTERVAL 31 DAY)),
(9042, 9009, 9022, DATE_SUB(NOW(), INTERVAL 26 DAY)),
(9043, 9009, 9029, DATE_SUB(NOW(), INTERVAL 18 DAY)),
(9044, 9009, 9038, DATE_SUB(NOW(), INTERVAL 9 DAY)),
(9045, 9009, 9040, DATE_SUB(NOW(), INTERVAL 3 DAY)),

(9046, 9010, 9007, DATE_SUB(NOW(), INTERVAL 21 DAY)),
(9047, 9010, 9015, DATE_SUB(NOW(), INTERVAL 16 DAY)),
(9048, 9010, 9033, DATE_SUB(NOW(), INTERVAL 11 DAY)),
(9049, 9010, 9038, DATE_SUB(NOW(), INTERVAL 5 DAY)),

(9050, 9011, 9003, DATE_SUB(NOW(), INTERVAL 20 DAY)),
(9051, 9011, 9015, DATE_SUB(NOW(), INTERVAL 19 DAY)),
(9052, 9011, 9022, DATE_SUB(NOW(), INTERVAL 18 DAY)),
(9053, 9011, 9038, DATE_SUB(NOW(), INTERVAL 9 DAY)),
(9054, 9011, 9039, DATE_SUB(NOW(), INTERVAL 6 DAY)),
(9055, 9011, 9040, DATE_SUB(NOW(), INTERVAL 4 DAY)),
(9056, 9011, 9007, DATE_SUB(NOW(), INTERVAL 25 DAY)),

(9057, 9012, 9003, DATE_SUB(NOW(), INTERVAL 15 DAY)),
(9058, 9012, 9007, DATE_SUB(NOW(), INTERVAL 14 DAY)),
(9059, 9012, 9015, DATE_SUB(NOW(), INTERVAL 13 DAY)),
(9060, 9012, 9022, DATE_SUB(NOW(), INTERVAL 12 DAY)),
(9061, 9012, 9027, DATE_SUB(NOW(), INTERVAL 8 DAY)),
(9062, 9012, 9029, DATE_SUB(NOW(), INTERVAL 6 DAY)),
(9063, 9012, 9033, DATE_SUB(NOW(), INTERVAL 2 DAY));

-- 关注关系（17 条，形成一个小型关注网络；不能关注自己）
INSERT INTO `follow` (`id`, `follower_id`, `following_id`, `create_time`) VALUES
(9001, 9001, 9007, DATE_SUB(NOW(), INTERVAL 40 DAY)),
(9002, 9001, 9009, DATE_SUB(NOW(), INTERVAL 28 DAY)),
(9003, 9002, 9007, DATE_SUB(NOW(), INTERVAL 35 DAY)),
(9004, 9002, 9010, DATE_SUB(NOW(), INTERVAL 19 DAY)),
(9005, 9003, 9009, DATE_SUB(NOW(), INTERVAL 30 DAY)),
(9006, 9003, 9008, DATE_SUB(NOW(), INTERVAL 24 DAY)),
(9007, 9004, 9007, DATE_SUB(NOW(), INTERVAL 22 DAY)),
(9008, 9005, 9009, DATE_SUB(NOW(), INTERVAL 18 DAY)),
(9009, 9005, 9011, DATE_SUB(NOW(), INTERVAL 11 DAY)),
(9010, 9006, 9010, DATE_SUB(NOW(), INTERVAL 9 DAY)),
(9011, 9007, 9011, DATE_SUB(NOW(), INTERVAL 16 DAY)),
(9012, 9007, 9009, DATE_SUB(NOW(), INTERVAL 12 DAY)),
(9013, 9008, 9007, DATE_SUB(NOW(), INTERVAL 20 DAY)),
(9014, 9009, 9007, DATE_SUB(NOW(), INTERVAL 17 DAY)),
(9015, 9009, 9012, DATE_SUB(NOW(), INTERVAL 7 DAY)),
(9016, 9010, 9011, DATE_SUB(NOW(), INTERVAL 15 DAY)),
(9017, 9012, 9009, DATE_SUB(NOW(), INTERVAL 5 DAY));


-- =============================================================
-- 11. 聊天记录：5 组买卖对话，共 32 条消息
--     内容围绕「问细节 → 议价 → 约地点 → 成交/没成交」，包含一组没谈成的，
--     演示数据全是大团圆反而不真实
-- =============================================================
INSERT INTO `chat_session`
(`id`, `buyer_id`, `seller_id`, `product_id`, `last_message`, `last_time`,
 `buyer_unread`, `seller_unread`, `buyer_deleted`, `seller_deleted`, `create_time`, `update_time`)
VALUES
(9001, 9001, 9007, 9003, '那就周日下午三点，图书馆门口见？',
 DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 40 MINUTE, 0, 1, 0, 0,
 DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 10 MINUTE, DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 40 MINUTE),
(9002, 9004, 9011, 9033, '可以，操场东门那边空地大，我等你试骑',
 DATE_SUB(NOW(), INTERVAL 20 HOUR), 0, 1, 0, 0,
 DATE_SUB(NOW(), INTERVAL 22 HOUR), DATE_SUB(NOW(), INTERVAL 20 HOUR)),
(9003, 9002, 9007, 9007, '好的，谢谢学姐，我明天上午过去拿',
 DATE_SUB(NOW(), INTERVAL 4 DAY), 0, 0, 0, 0,
 DATE_SUB(NOW(), INTERVAL 4 DAY) - INTERVAL 30 MINUTE, DATE_SUB(NOW(), INTERVAL 4 DAY)),
(9004, 9003, 9012, 9038, '那我下午过来看看，方便吗',
 DATE_SUB(NOW(), INTERVAL 9 HOUR), 0, 1, 0, 0,
 DATE_SUB(NOW(), INTERVAL 11 HOUR), DATE_SUB(NOW(), INTERVAL 9 HOUR)),
(9005, 9005, 9009, 9017, '那算了，我床是 1.2 米的，还是谢谢你',
 DATE_SUB(NOW(), INTERVAL 27 DAY), 0, 0, 0, 0,
 DATE_SUB(NOW(), INTERVAL 27 DAY) - INTERVAL 25 MINUTE, DATE_SUB(NOW(), INTERVAL 27 DAY));

INSERT INTO `chat_message`
(`id`, `session_id`, `sender_id`, `receiver_id`, `msg_type`, `content`, `is_read`, `create_time`)
VALUES
-- 会话 9001：罗技鼠标（议价成交）
(9001, 9001, 9001, 9007, 1, '在吗？这个鼠标还在吗，滚轮有没有漂移？', 1, DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 10 MINUTE),
(9002, 9001, 9007, 9001, 1, '在的，滚轮正常，我平时画图一直在用，没有跳格', 1, DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 13 MINUTE),
(9003, 9001, 9001, 9007, 1, '能便宜点吗，300 可以吗', 1, DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 18 MINUTE),
(9004, 9001, 9007, 9001, 1, '330 吧，收纳袋和充电线都一起给你，成色真的挺好', 1, DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 21 MINUTE),
(9005, 9001, 9001, 9007, 1, '行，那就 330，什么时候方便看货', 1, DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 26 MINUTE),
(9006, 9001, 9007, 9001, 1, '明天下午四点以后我都在宿舍，可以来 1 号楼 305', 1, DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 30 MINUTE),
(9007, 9001, 9001, 9007, 1, '明天我有课，周末可以吗', 1, DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 35 MINUTE),
(9008, 9001, 9007, 9001, 1, '那就周日下午三点，图书馆门口见？', 0, DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 40 MINUTE),

-- 会话 9002：电动滑板车（问续航、约试骑）
(9009, 9002, 9004, 9011, 1, '滑板车续航大概多少？我上课单程两公里', 1, DATE_SUB(NOW(), INTERVAL 22 HOUR)),
(9010, 9002, 9011, 9004, 1, '两公里完全没问题，我每天来回四趟，三天充一次', 1, DATE_SUB(NOW(), INTERVAL 21 HOUR)),
(9011, 9002, 9004, 9011, 1, '电池换过吗，轮胎是实心的还是充气的', 1, DATE_SUB(NOW(), INTERVAL 21 HOUR)),
(9012, 9002, 9011, 9004, 1, '电池没换过，原装的。轮胎是实心免充气，不怕扎', 1, DATE_SUB(NOW(), INTERVAL 21 HOUR)),
(9013, 9002, 9004, 9011, 1, '那我考虑一下，明天能试骑吗', 1, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
(9014, 9002, 9011, 9004, 1, '可以，操场东门那边空地大，我等你试骑', 0, DATE_SUB(NOW(), INTERVAL 20 HOUR)),

-- 会话 9003：考研数学（问书写情况，成交）
(9015, 9003, 9002, 9007, 1, '学姐，考研数学这本还在吗', 1, DATE_SUB(NOW(), INTERVAL 4 DAY) - INTERVAL 40 MINUTE),
(9016, 9003, 9007, 9002, 1, '在的，前六章做过，后面全新', 1, DATE_SUB(NOW(), INTERVAL 4 DAY) - INTERVAL 38 MINUTE),
(9017, 9003, 9002, 9007, 1, '有划线吗？我比较介意这个', 1, DATE_SUB(NOW(), INTERVAL 4 DAY) - INTERVAL 35 MINUTE),
(9018, 9003, 9007, 9002, 1, '没有划线，只在课后题空白处写过过程，可以用橡皮擦掉', 1, DATE_SUB(NOW(), INTERVAL 4 DAY) - INTERVAL 33 MINUTE),
(9019, 9003, 9002, 9007, 1, '好的，谢谢学姐，我明天上午过去拿', 0, DATE_SUB(NOW(), INTERVAL 4 DAY)),

-- 会话 9004：吹风机（问功率噪音，准备看货）
(9020, 9004, 9003, 9012, 1, '吹风机风力大吗，长发大概多久能干', 1, DATE_SUB(NOW(), INTERVAL 11 HOUR)),
(9021, 9004, 9012, 9003, 1, '2200W 的，我齐肩发五分钟就干了', 1, DATE_SUB(NOW(), INTERVAL 11 HOUR)),
(9022, 9004, 9003, 9012, 1, '噪音大不大？宿舍晚上用会不会被投诉', 1, DATE_SUB(NOW(), INTERVAL 10 HOUR)),
(9023, 9004, 9012, 9003, 1, '比宿舍那种小功率的大一些，但也不算吵，十点前用没问题', 1, DATE_SUB(NOW(), INTERVAL 10 HOUR)),
(9024, 9004, 9003, 9012, 1, '可以先看看实物再决定吗', 1, DATE_SUB(NOW(), INTERVAL 10 HOUR)),
(9025, 9004, 9012, 9003, 1, '可以啊，你什么时候方便', 1, DATE_SUB(NOW(), INTERVAL 10 HOUR)),
(9026, 9004, 9003, 9012, 1, '那我下午过来看看，方便吗', 0, DATE_SUB(NOW(), INTERVAL 9 HOUR)),

-- 会话 9005：四件套（尺码不合适，没成交）
(9027, 9005, 9005, 9009, 1, '四件套是纯棉的吗', 1, DATE_SUB(NOW(), INTERVAL 27 DAY) - INTERVAL 25 MINUTE),
(9028, 9005, 9009, 9005, 1, '是的，100% 全棉，洗过两次没有起球', 1, DATE_SUB(NOW(), INTERVAL 27 DAY) - INTERVAL 22 MINUTE),
(9029, 9005, 9005, 9009, 1, '1.2 米的床能铺吗', 1, DATE_SUB(NOW(), INTERVAL 27 DAY) - INTERVAL 18 MINUTE),
(9030, 9005, 9009, 9005, 1, '会大一些，我这是 1.5 米床的，建议先量一下床垫尺寸', 1, DATE_SUB(NOW(), INTERVAL 27 DAY) - INTERVAL 15 MINUTE),
(9031, 9005, 9005, 9009, 1, '那我量一下再说', 1, DATE_SUB(NOW(), INTERVAL 27 DAY) - INTERVAL 10 MINUTE),
(9032, 9005, 9005, 9009, 1, '那算了，我床是 1.2 米的，还是谢谢你', 0, DATE_SUB(NOW(), INTERVAL 27 DAY));


-- =============================================================
-- 12. 举报 5 条
--     商品举报 2（1 已处理：下架；1 待处理）
--     用户举报 2（1 已处理：封禁；1 已驳回）
--     评价举报 1（待处理）
--     target_snapshot 是提交瞬间的对象快照，之后对象被删/被改也查得到原样
-- =============================================================
-- 说明：target_snapshot 的字段名必须和 ReportServiceImpl.loadTarget() 生成的快照一致
--       （商品用 kind/title/cover/price/status，用户用 kind/nickname/avatar，评价用 kind/content/stars），
--       后台的「对象」列只认这套键，少写一个 kind 就会显示成空。
INSERT INTO `report`
(`id`, `reporter_id`, `report_type`, `target_id`, `target_user_id`, `target_snapshot`, `reason`,
 `content`, `images`, `status`, `handler_id`, `accept_time`, `handle_result`, `handle_action`,
 `handle_time`, `create_time`, `update_time`, `deleted`)
VALUES
-- 12.1 商品举报（已处理 - 强制下架）：显卡货不对板、疑似矿卡
(9001, 9002, 1, 9006, 9008,
 JSON_OBJECT('kind', 'product', 'title', '显卡 RTX 3060 8G 拆机卡 无保修',
             'description', '朋友工作室升级换下来的，成色一般，风扇有点灰，没有原包装也没有发票。',
             'price', 980.00, 'status', 1, 'statusLabel', '已上架',
             'cover', '/uploads/demo/cover-1.png',
             'createTime', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 40 DAY), '%Y-%m-%d %H:%i:%s')),
 2, '商品描述里说没修过，但实物散热器有拆装痕迹，核心附近还有硅脂溢出，明显是矿卡翻新。页面上又写「矿卡勿拍」，描述前后矛盾。',
 NULL, 2, 2, DATE_SUB(NOW(), INTERVAL 36 DAY),
 '举报成立：商品描述与实际不符，且属于平台禁止的矿卡类商品，已强制下架并对卖家扣除信誉分。',
 1, DATE_SUB(NOW(), INTERVAL 35 DAY),
 DATE_SUB(NOW(), INTERVAL 37 DAY), DATE_SUB(NOW(), INTERVAL 35 DAY), 0),

-- 12.2 商品举报（待处理）：卫衣疑似为仿品
(9002, 9003, 1, 9023, 9010,
 JSON_OBJECT('kind', 'product', 'title', '加绒加厚卫衣 全新带吊牌 可小刀',
             'description', '网购的，尺码不合适退货又超时了，吊牌还在，一次都没穿过。',
             'price', 68.00, 'status', 1, 'statusLabel', '已上架',
             'cover', '/uploads/demo/cover-4.png',
             'createTime', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 26 DAY), '%Y-%m-%d %H:%i:%s')),
 1, '说是品牌卫衣全新带吊牌，但吊牌上的型号和官网对不上，领标印刷也很粗糙，怀疑是高仿。',
 NULL, 0, NULL, NULL, NULL, 0, NULL,
 DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 0),

-- 12.3 用户举报（已驳回）：怀疑卖家是骗子，经核实为沟通误会
(9003, 9004, 2, 9012, 9012,
 JSON_OBJECT('kind', 'user', 'nickname', '孙嘉宁',
             'avatar', '/uploads/demo/avatar-12.png',
             'intro', '大三在读。之前有两笔交易因为描述不清引起过误会，已经按平台要求补充说明。',
             'role', 'student', 'roleLabel', '认证学生', 'studentVerified', 1,
             'creditScore', 55, 'status', 0),
 3, '拍下之后卖家一直不回消息，也不发货，怀疑是想骗钱。',
 NULL, 3, 2, DATE_SUB(NOW(), INTERVAL 13 DAY),
 '经核实：卖家当时在考试周，手机不在身边，聊天记录显示已于次日回复并发货，不存在诈骗行为，举报不成立。',
 0, DATE_SUB(NOW(), INTERVAL 12 DAY),
 DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), 0),

-- 12.4 用户举报（已处理 - 封禁）：多次收款不发货
(9004, 9005, 2, 9006, 9006,
 JSON_OBJECT('kind', 'user', 'nickname', '何思远',
             'avatar', '/uploads/demo/avatar-6.png',
             'intro', '爱好骑行和露营，装备党一枚。近期因为一次交易纠纷被平台处理过，正在申诉。',
             'role', 'user', 'roleLabel', '普通用户', 'studentVerified', 0,
             'creditScore', 65, 'status', 0),
 3, '这个人收了我室友的钱一直不发货，问就说在寄了，拖了半个月，聊天记录都有截图。',
 NULL, 2, 2, DATE_SUB(NOW(), INTERVAL 10 DAY),
 '举报成立：该用户存在收款不发货行为，且有同类举报记录，已封禁账号并扣除信誉分。',
 2, DATE_SUB(NOW(), INTERVAL 9 DAY),
 DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), 0),

-- 12.5 评价举报（待处理）：差评内容与事实不符
(9005, 9001, 3, 9017, 9012,
 JSON_OBJECT('kind', 'review', 'stars', 1, 'isAnonymous', 0, 'productId', 9032,
             'content', '车到手发现后刹车是松的，链条也有异响，和说的刚做过保养完全对不上，很失望。',
             'images', NULL,
             'createTime', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 6 DAY), '%Y-%m-%d %H:%i:%s')),
 6, '这条评价说车没保养，但我在聊天里已经说明了刹车线是运输中松的、可以随时过来调，评价内容有误导性。',
 NULL, 0, NULL, NULL, NULL, 0, NULL,
 DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 0);

-- 评价类举报的快照里补上订单号（订单号是运行时生成的，写死在 VALUES 里迟早会和订单表对不上）
UPDATE `report` r JOIN `review` v ON v.`id` = r.`target_id`
SET r.`target_snapshot` = JSON_SET(r.`target_snapshot`, '$.orderNo', v.`order_no`)
WHERE r.`report_type` = 3 AND r.`id` BETWEEN 9001 AND 9005;


-- =============================================================
-- 13. 首页轮播图 3 张（复用 V12 预置的演示图）
-- =============================================================
INSERT INTO `banner`
(`id`, `title`, `image_url`, `link_type`, `link_value`, `position`, `sort`, `status`,
 `start_time`, `end_time`, `create_time`, `update_time`, `deleted`)
VALUES
(9001, '毕业季 · 二手教材专区', '/uploads/2026/10/seed-banner-1.png', 2, '2', 1, 1, 1,
 DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_ADD(NOW(), INTERVAL 60 DAY),
 DATE_SUB(NOW(), INTERVAL 20 DAY), NOW(), 0),
(9002, '数码好物 · 闲置出清', '/uploads/2026/10/seed-banner-2.png', 2, '1', 1, 2, 1,
 DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_ADD(NOW(), INTERVAL 60 DAY),
 DATE_SUB(NOW(), INTERVAL 20 DAY), NOW(), 0),
(9003, '认证学生发布 · 平台审核上架', '/uploads/2026/10/seed-banner-3.png', 4, NULL, 1, 3, 1,
 DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_ADD(NOW(), INTERVAL 60 DAY),
 DATE_SUB(NOW(), INTERVAL 20 DAY), NOW(), 0);


-- =============================================================
-- 14. 首页配置（公告 1 条 + 热门推荐按热度自动）
--     只改配置值，不删行：KV 表的特点是「缺行有默认值兜底」，这里直接覆盖
-- =============================================================
INSERT INTO `home_config` (`config_key`, `config_value`, `remark`) VALUES
('notice_text', '欢迎来到校园二手交易平台，毕业季特惠！', '首页公告文字，空则不显示'),
('notice_link', '', '公告点击跳转地址，可空'),
('notice_enabled', '1', '公告是否显示:0否 1是'),
('hot_mode', 'auto', '热门推荐模式:manual手动选品 auto按热度'),
('hot_product_ids', '[]', '手动模式选中的商品ID（JSON数组，有序，最多20个）'),
('hot_count', '10', '自动模式取前N件（1-20）'),
('new_count', '10', '新品推荐取最新N件（1-20）')
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);


-- =============================================================
-- 15. 回填计数（商品收藏数、用户收藏/关注/粉丝数）
--     计数器写死是最容易出问题的一类假数据：页面显示 40 人收藏，
--     点进去只有 3 条，一眼就穿帮。这里统一按明细表真实行数回填。
-- =============================================================
UPDATE `product` p
SET p.`favorite_count` = (SELECT COUNT(*) FROM `favorite` f WHERE f.`product_id` = p.`id`)
WHERE p.`id` BETWEEN 9001 AND 9040;

UPDATE `sys_user` u
SET u.`favorite_count` = (SELECT COUNT(*) FROM `favorite` f WHERE f.`user_id` = u.`id`),
    u.`follow_count`   = (SELECT COUNT(*) FROM `follow` fo WHERE fo.`follower_id` = u.`id`),
    u.`fans_count`     = (SELECT COUNT(*) FROM `follow` fo WHERE fo.`following_id` = u.`id`)
WHERE u.`id` BETWEEN 9001 AND 9012;


-- =============================================================
-- 16. 统计输出（执行完直接看结果，不用再手工 count）
-- =============================================================
SELECT '========== 演示数据统计 ==========' AS `统计`;

SELECT '1. 测试用户' AS `数据块`, COUNT(*) AS `条数` FROM `sys_user` WHERE `id` BETWEEN 9001 AND 9012
UNION ALL SELECT '   ├ 普通用户', COUNT(*) FROM `sys_user` WHERE `id` BETWEEN 9001 AND 9012 AND `role` = 'user'
UNION ALL SELECT '   ├ 认证学生', COUNT(*) FROM `sys_user` WHERE `id` BETWEEN 9001 AND 9012 AND `role` = 'student'
UNION ALL SELECT '   └ 被封禁账号', COUNT(*) FROM `sys_user` WHERE `id` BETWEEN 9001 AND 9012 AND `status` = 1
UNION ALL SELECT '2. 学生认证', COUNT(*) FROM `student_auth` WHERE `user_id` BETWEEN 9001 AND 9012
UNION ALL SELECT '3. 收货地址', COUNT(*) FROM `address` WHERE `user_id` BETWEEN 9001 AND 9012
UNION ALL SELECT '4. 信誉分流水', COUNT(*) FROM `credit_log` WHERE `user_id` BETWEEN 9001 AND 9012
UNION ALL SELECT '5. 商品', COUNT(*) FROM `product` WHERE `id` BETWEEN 9001 AND 9040
UNION ALL SELECT '   ├ 在售', COUNT(*) FROM `product` WHERE `id` BETWEEN 9001 AND 9040 AND `status` = 1
UNION ALL SELECT '   ├ 已售出', COUNT(*) FROM `product` WHERE `id` BETWEEN 9001 AND 9040 AND `status` = 3
UNION ALL SELECT '   ├ 已下架', COUNT(*) FROM `product` WHERE `id` BETWEEN 9001 AND 9040 AND `status` = 2
UNION ALL SELECT '   ├ 违规下架', COUNT(*) FROM `product` WHERE `id` BETWEEN 9001 AND 9040 AND `status` = 4
UNION ALL SELECT '   ├ 待审核', COUNT(*) FROM `product` WHERE `id` BETWEEN 9001 AND 9040 AND `status` = 0
UNION ALL SELECT '   └ 待人工复核', COUNT(*) FROM `product` WHERE `id` BETWEEN 9001 AND 9040 AND `need_manual_review` = 1
UNION ALL SELECT '6. 商品图片', COUNT(*) FROM `product_image` WHERE `product_id` BETWEEN 9001 AND 9040
UNION ALL SELECT '7. 订单', COUNT(*) FROM `trade_order` WHERE `id` BETWEEN 9001 AND 9026
UNION ALL SELECT '   ├ 待付款', COUNT(*) FROM `trade_order` WHERE `id` BETWEEN 9001 AND 9026 AND `status` = 0
UNION ALL SELECT '   ├ 待发货', COUNT(*) FROM `trade_order` WHERE `id` BETWEEN 9001 AND 9026 AND `status` = 1
UNION ALL SELECT '   ├ 待收货', COUNT(*) FROM `trade_order` WHERE `id` BETWEEN 9001 AND 9026 AND `status` = 2
UNION ALL SELECT '   ├ 已完成', COUNT(*) FROM `trade_order` WHERE `id` BETWEEN 9001 AND 9026 AND `status` = 3
UNION ALL SELECT '   ├ 已取消', COUNT(*) FROM `trade_order` WHERE `id` BETWEEN 9001 AND 9026 AND `status` = 4
UNION ALL SELECT '   └ 退款中', COUNT(*) FROM `trade_order` WHERE `id` BETWEEN 9001 AND 9026 AND `status` = 5
UNION ALL SELECT '8. 订单日志', COUNT(*) FROM `order_log` WHERE `order_id` BETWEEN 9001 AND 9026
UNION ALL SELECT '9. 评价', COUNT(*) FROM `review` WHERE `id` BETWEEN 9001 AND 9018
UNION ALL SELECT '   ├ 5 星', COUNT(*) FROM `review` WHERE `id` BETWEEN 9001 AND 9018 AND `stars` = 5
UNION ALL SELECT '   ├ 4 星', COUNT(*) FROM `review` WHERE `id` BETWEEN 9001 AND 9018 AND `stars` = 4
UNION ALL SELECT '   ├ 3 星', COUNT(*) FROM `review` WHERE `id` BETWEEN 9001 AND 9018 AND `stars` = 3
UNION ALL SELECT '   ├ 2 星', COUNT(*) FROM `review` WHERE `id` BETWEEN 9001 AND 9018 AND `stars` = 2
UNION ALL SELECT '   ├ 1 星', COUNT(*) FROM `review` WHERE `id` BETWEEN 9001 AND 9018 AND `stars` = 1
UNION ALL SELECT '   ├ 带追评', COUNT(*) FROM `review` WHERE `id` BETWEEN 9001 AND 9018 AND `follow_time` IS NOT NULL
UNION ALL SELECT '   └ 匿名', COUNT(*) FROM `review` WHERE `id` BETWEEN 9001 AND 9018 AND `is_anonymous` = 1
UNION ALL SELECT '10. 收藏', COUNT(*) FROM `favorite` WHERE `user_id` BETWEEN 9001 AND 9012
UNION ALL SELECT '11. 关注', COUNT(*) FROM `follow` WHERE `follower_id` BETWEEN 9001 AND 9012
UNION ALL SELECT '12. 聊天会话', COUNT(*) FROM `chat_session` WHERE `id` BETWEEN 9001 AND 9005
UNION ALL SELECT '13. 聊天消息', COUNT(*) FROM `chat_message` WHERE `session_id` BETWEEN 9001 AND 9005
UNION ALL SELECT '14. 举报', COUNT(*) FROM `report` WHERE `id` BETWEEN 9001 AND 9005
UNION ALL SELECT '   ├ 待处理', COUNT(*) FROM `report` WHERE `id` BETWEEN 9001 AND 9005 AND `status` = 0
UNION ALL SELECT '   ├ 已处理', COUNT(*) FROM `report` WHERE `id` BETWEEN 9001 AND 9005 AND `status` = 2
UNION ALL SELECT '   └ 已驳回', COUNT(*) FROM `report` WHERE `id` BETWEEN 9001 AND 9005 AND `status` = 3
UNION ALL SELECT '15. 轮播图', COUNT(*) FROM `banner` WHERE `id` BETWEEN 9001 AND 9003
UNION ALL SELECT '16. 首页公告', COUNT(*) FROM `home_config` WHERE `config_key` = 'notice_text';

-- 数据自洽性校验（结果应全部为 0）
SELECT '========== 一致性校验（应全为 0）==========' AS `校验`;

SELECT '订单金额与商品价格不一致' AS `检查项`, COUNT(*) AS `异常数`
FROM `trade_order` o JOIN `product` p ON p.`id` = o.`product_id`
WHERE o.`id` BETWEEN 9001 AND 9026 AND o.`price` <> p.`price`
UNION ALL
SELECT '买家与卖家是同一人', COUNT(*) FROM `trade_order`
WHERE `id` BETWEEN 9001 AND 9026 AND `buyer_id` = `seller_id`
UNION ALL
SELECT '订单时间早于商品发布时间', COUNT(*) FROM `trade_order` o JOIN `product` p ON p.`id` = o.`product_id`
WHERE o.`id` BETWEEN 9001 AND 9026 AND o.`create_time` < p.`create_time`
UNION ALL
SELECT '占用的订单与已售出商品数不匹配', ABS(
  (SELECT COUNT(*) FROM `trade_order` WHERE `id` BETWEEN 9001 AND 9026 AND `status` IN (1,2,3,5))
  - (SELECT COUNT(*) FROM `product` WHERE `id` BETWEEN 9001 AND 9040 AND `status` = 3))
UNION ALL
SELECT '评价对应的订单不是已完成', COUNT(*) FROM `review` r JOIN `trade_order` o ON o.`id` = r.`order_id`
WHERE r.`id` BETWEEN 9001 AND 9018 AND o.`status` <> 3
UNION ALL
SELECT '评价时间早于订单完成时间', COUNT(*) FROM `review` r JOIN `trade_order` o ON o.`id` = r.`order_id`
WHERE r.`id` BETWEEN 9001 AND 9018 AND r.`create_time` < o.`finish_time`
UNION ALL
SELECT '商品收藏数与收藏记录不一致', COUNT(*) FROM `product` p
WHERE p.`id` BETWEEN 9001 AND 9040
  AND p.`favorite_count` <> (SELECT COUNT(*) FROM `favorite` f WHERE f.`product_id` = p.`id`)
UNION ALL
SELECT '自己关注自己', COUNT(*) FROM `follow`
WHERE `follower_id` BETWEEN 9001 AND 9012 AND `follower_id` = `following_id`
UNION ALL
SELECT '商品图片数量不是 2 张', COUNT(*) FROM `product` p
WHERE p.`id` BETWEEN 9001 AND 9040
  AND (SELECT COUNT(*) FROM `product_image` pi WHERE pi.`product_id` = p.`id`) <> 2;

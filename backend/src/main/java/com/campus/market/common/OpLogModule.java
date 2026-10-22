// 文件路径: src/main/java/com/campus/market/common/OpLogModule.java
package com.campus.market.common;

/**
 * 操作日志的模块码与动作码常量。
 * <p>
 * 集中在这里而不是在注解里直接写字符串：日志要按模块分组统计，
 * 一旦有人在注解里手写成 "Report"（大写 R）就会变成另一个分组，统计时少一块。
 */
public final class OpLogModule {

    public static final String REPORT = "report";
    public static final String USER = "user";
    public static final String PRODUCT = "product";
    public static final String REVIEW = "review";
    public static final String ORDER = "order";
    public static final String STUDENT_AUTH = "student_auth";

    /** 敏感词词典维护（模块11） */
    public static final String SENSITIVE_WORD = "sensitive_word";

    /** 后台账号维护（模块11） */
    public static final String ACCOUNT = "account";

    /** 首页推荐位配置（模块12）：轮播图、公告、金刚区、热门/新品 */
    public static final String HOME = "home";

    /** 客服接单 */
    public static final String ACTION_ACCEPT = "accept";

    /** 处理举报 */
    public static final String ACTION_HANDLE = "handle";

    /** 强制下架 */
    public static final String ACTION_OFFLINE = "offline";

    /** 封禁 / 解封 */
    public static final String ACTION_BAN = "ban";

    /** 修改用户资料 */
    public static final String ACTION_UPDATE_PROFILE = "update_profile";

    /** 变更角色 */
    public static final String ACTION_UPDATE_ROLE = "update_role";

    /** 删除 */
    public static final String ACTION_DELETE = "delete";

    /** 审核 */
    public static final String ACTION_AUDIT = "audit";

    /** 新增（后台账号 / 敏感词） */
    public static final String ACTION_CREATE = "create";

    /** 编辑 */
    public static final String ACTION_UPDATE = "update";

    /** 启用 / 禁用 */
    public static final String ACTION_STATUS = "status";

    /** 重置密码：高危操作，必须留痕 */
    public static final String ACTION_RESET_PASSWORD = "reset_password";

    /** 批量导入 */
    public static final String ACTION_BATCH_IMPORT = "batch_import";

    /** 排序（首页轮播图 / 金刚区拖拽保存） */
    public static final String ACTION_SORT = "sort";

    private OpLogModule() {
    }

    public static String label(String module) {
        if (module == null) {
            return "未知";
        }
        return switch (module) {
            case REPORT -> "举报处理";
            case USER -> "用户管理";
            case PRODUCT -> "商品管理";
            case REVIEW -> "评价管理";
            case ORDER -> "订单管理";
            case STUDENT_AUTH -> "学生认证";
            case SENSITIVE_WORD -> "敏感词管理";
            case ACCOUNT -> "账号管理";
            case HOME -> "首页配置";
            default -> module;
        };
    }

    public static String actionLabel(String action) {
        if (action == null) {
            return "未知";
        }
        return switch (action) {
            case ACTION_ACCEPT -> "接单";
            case ACTION_HANDLE -> "处理";
            case ACTION_OFFLINE -> "强制下架";
            case ACTION_BAN -> "封禁/解封";
            case ACTION_UPDATE_PROFILE -> "修改资料";
            case ACTION_UPDATE_ROLE -> "变更角色";
            case ACTION_DELETE -> "删除";
            case ACTION_AUDIT -> "审核";
            case ACTION_CREATE -> "新增";
            case ACTION_UPDATE -> "编辑";
            case ACTION_STATUS -> "启用/禁用";
            case ACTION_RESET_PASSWORD -> "重置密码";
            case ACTION_BATCH_IMPORT -> "批量导入";
            case ACTION_SORT -> "排序";
            default -> action;
        };
    }
}
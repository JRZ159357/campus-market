// 文件路径: src/main/java/com/campus/market/common/ResultCode.java
package com.campus.market.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 状态码枚举。业务失败统一 500，仅参数/认证/权限类单独登记。
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无访问权限"),
    NOT_FOUND(404, "资源不存在"),
    ERROR(500, "操作失败");

    private final Integer code;
    private final String msg;
}

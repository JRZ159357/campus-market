// 文件路径: src/main/java/com/campus/market/common/BusinessException.java
package com.campus.market.common;

import lombok.Getter;

/**
 * 业务异常。由 Service 层主动抛出，统一由 GlobalExceptionHandler 转为 Result。
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.ERROR.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}

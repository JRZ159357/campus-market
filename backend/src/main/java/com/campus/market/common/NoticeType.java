// 文件路径: src/main/java/com/campus/market/common/NoticeType.java
package com.campus.market.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 系统通知类型。前端按类型选图标与跳转方式：
 * 订单通知跳订单详情、审核通知跳对应业务页、系统公告只读、举报处理跳商品或用户。
 */
@Getter
@AllArgsConstructor
public enum NoticeType {

    ORDER(1, "订单通知"),
    AUDIT(2, "审核通知"),
    ANNOUNCEMENT(3, "系统公告"),
    REPORT(4, "举报处理");

    private final Integer code;
    private final String label;

    public static NoticeType of(Integer code) {
        for (NoticeType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return ANNOUNCEMENT;
    }
}

// 文件路径: src/main/java/com/campus/market/common/MsgType.java
package com.campus.market.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 聊天消息类型。
 * <p>
 * 消息内容是多态的，同一张 chat_message 表用 msg_type 区分：
 * 文本与图片的 content 是原文/URL，商品卡片与订单卡片的 content 是序列化后的 JSON，
 * 前端按 msg_type 决定用哪种组件渲染。
 */
@Getter
@AllArgsConstructor
public enum MsgType {

    TEXT(1, "文本"),
    IMAGE(2, "图片"),
    SYSTEM(3, "系统消息"),
    PRODUCT_CARD(4, "商品卡片"),
    ORDER_CARD(5, "订单卡片");

    private final Integer code;
    private final String label;

    public static MsgType of(Integer code) {
        for (MsgType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return TEXT;
    }

    /**
     * 会话列表里的「最后一条消息」摘要。卡片类消息在列表里只显示占位文案，
     * 避免把整段 JSON 塞进列表。
     */
    public static String preview(Integer code, String content) {
        return switch (of(code)) {
            case IMAGE -> "[图片]";
            case SYSTEM -> "[系统消息] " + abbreviate(content);
            case PRODUCT_CARD -> "[商品]";
            case ORDER_CARD -> "[订单]";
            default -> abbreviate(content);
        };
    }

    private static String abbreviate(String content) {
        if (content == null) {
            return "";
        }
        String plain = content.replaceAll("\\s+", " ").trim();
        return plain.length() > 60 ? plain.substring(0, 60) + "…" : plain;
    }
}

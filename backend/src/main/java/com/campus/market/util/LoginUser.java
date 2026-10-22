// 文件路径: src/main/java/com/campus/market/util/LoginUser.java
package com.campus.market.util;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录用户，存入 ThreadLocal 供 Service 层使用。
 */
@Data
@AllArgsConstructor
public class LoginUser {

    private Long userId;

    private String role;
}

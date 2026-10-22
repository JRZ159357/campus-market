// 文件路径: src/main/java/com/campus/market/vo/AdminAccountVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台账号列表项。
 * <p>
 * 不返回 password（连加密后的都不返回）：BCrypt 哈希虽然不是明文，
 * 但泄露哈希等于把离线爆破的入口摆出来，而列表页根本不需要它。
 */
@Data
@Schema(description = "后台账号列表项")
public class AdminAccountVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "角色码:admin / customer_service")
    private String role;

    @Schema(description = "角色中文名")
    private String roleLabel;

    @Schema(description = "状态:0正常 1禁用")
    private Integer status;

    @Schema(description = "状态中文名")
    private String statusLabel;

    @Schema(description = "最后登录时间，从未登录为 null")
    private LocalDateTime lastLoginTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 是否为当前登录账号。
     * <p>
     * 由后端算好给前端，而不是让前端比对 userId：前端需要据此
     * 禁用「删除 / 禁用」按钮，判断逻辑放后端就不会出现
     * 「前端算错了导致能点自己」的情况。
     */
    @Schema(description = "是否为当前登录账号")
    private Boolean self;
}

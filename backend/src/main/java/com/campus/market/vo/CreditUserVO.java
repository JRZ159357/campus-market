// 文件路径: src/main/java/com/campus/market/vo/CreditUserVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 信誉分管理页的用户行。
 * <p>
 * 只带管理端调分需要的字段（身份 + 分数 + 等级），不做成完整的用户管理 VO：
 * 用户管理是后续模块的事，这里只解决「找到人、看分数、调分数」这一个闭环，
 * 字段越少越不容易和将来的用户模块冲突。
 */
@Data
@Schema(description = "信誉分管理用户行")
public class CreditUserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "账号（后台账号才有）")
    private String username;

    @Schema(description = "角色")
    private String role;

    @Schema(description = "状态:0正常 1封禁")
    private Integer status;

    @Schema(description = "信誉分")
    private Integer score;

    @Schema(description = "等级编码")
    private String level;

    @Schema(description = "等级名称")
    private String levelLabel;

    @Schema(description = "等级颜色")
    private String levelColor;
}

// 文件路径: src/main/java/com/campus/market/dto/UserProfileUpdateDTO.java
package com.campus.market.dto;

import com.campus.market.common.UserRule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人资料入参。
 * <p>
 * 所有字段都是「非空即更新」语义：只传要改的字段，不传的字段保持原值。
 * 之所以不用「全量覆盖」语义，是因为个人中心卡片会同时提交头像、昵称、
 * 性别、简介，若某一项在前端因异常变成 undefined，全量覆盖会把用户数据清空。
 */
@Data
@Schema(description = "修改个人资料入参")
public class UserProfileUpdateDTO {

    @Size(max = UserRule.NICKNAME_MAX_LENGTH, message = "昵称不能超过 20 个字")
    @Schema(description = "昵称")
    private String nickname;

    @Size(max = UserRule.AVATAR_MAX_LENGTH, message = "头像地址过长")
    @Schema(description = "头像地址")
    private String avatar;

    @Min(value = 0, message = "性别取值不合法")
    @Max(value = 2, message = "性别取值不合法")
    @Schema(description = "性别:0保密 1男 2女")
    private Integer gender;

    @Size(max = UserRule.INTRO_MAX_LENGTH, message = "个人简介不能超过 100 个字")
    @Schema(description = "个人简介")
    private String intro;
}

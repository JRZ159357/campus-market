// 文件路径: src/main/java/com/campus/market/dto/AdminUserQueryDTO.java
package com.campus.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理端用户列表查询参数。
 * <p>
 * 顶部标签与搜索栏共用这一个 DTO：
 * 标签切换本质就是「按角色或状态过滤」，与关键词是「与」的关系，
 * 拆成两套参数反而要在 Service 里手工合并。
 */
@Data
@Schema(description = "管理端用户列表查询参数")
public class AdminUserQueryDTO {

    @Schema(description = "页码，从1开始")
    private Long pageNum;

    @Schema(description = "每页条数，默认10，最大100")
    private Long pageSize;

    @Schema(description = "关键词：昵称 / 登录账号 / 手机号 / 学号 / 用户ID")
    private String keyword;

    @Schema(description = "角色：user普通用户 student认证学生 customer_service客服 admin管理员")
    private String role;

    @Schema(description = "账号状态：0正常 1封禁")
    private Integer status;

    @Schema(description = "学生认证：0未认证 1已认证")
    private Integer studentVerified;

    @Schema(description = "信誉分下限")
    private Integer minCredit;

    @Schema(description = "信誉分上限")
    private Integer maxCredit;

    @Schema(description = "注册时间起，格式 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss")
    private String startTime;

    @Schema(description = "注册时间止，格式 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss")
    private String endTime;
}

// 文件路径: src/main/java/com/campus/market/entity/SysOperationLog.java
// SQL 路径: sql/V10__report_operation_log.sql
package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统操作日志实体，对应表 sys_operation_log。
 * <p>
 * 由 {@code OperationLogAspect} 自动写入：Service 方法上打
 * {@code @OpLog} 注解即记录，代码里不需要手工插日志，
 * 因此不会发生「某个写操作忘了记日志」这种事后才发现的漏洞。
 * <p>
 * 没有 deleted 列：日志只追加不修改也不删除，
 * 加逻辑删除只会给人「日志可以被清掉」的错觉。
 */
@Data
@TableName("sys_operation_log")
@Schema(description = "系统操作日志")
public class SysOperationLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "操作人ID")
    private Long operatorId;

    @Schema(description = "操作人角色码")
    private String operatorRole;

    @Schema(description = "操作人昵称（冗余，改名后日志仍可读）")
    private String operatorName;

    @Schema(description = "业务模块:report/user/product/review/order/student_auth")
    private String module;

    @Schema(description = "动作:accept/handle/offline/ban/update_role/delete")
    private String action;

    @Schema(description = "操作目标ID")
    private Long targetId;

    @Schema(description = "操作详情（可读描述）")
    private String detail;

    @Schema(description = "操作来源IP")
    private String ip;

    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "操作时间")
    private LocalDateTime createTime;
}
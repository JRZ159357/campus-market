// 文件路径: src/main/java/com/campus/market/controller/SensitiveWordController.java
package com.campus.market.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.common.Result;
import com.campus.market.common.RoleConst;
import com.campus.market.common.annotation.RequireRole;
import com.campus.market.dto.SensitiveWordBatchDTO;
import com.campus.market.dto.SensitiveWordQueryDTO;
import com.campus.market.dto.SensitiveWordSaveDTO;
import com.campus.market.service.SensitiveWordService;
import com.campus.market.vo.SensitiveWordBatchVO;
import com.campus.market.vo.SensitiveWordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 敏感词管理接口。
 * <p>
 * 权限模型：读接口对管理员与客服都开放（客服处理举报时要能确认
 * 「这个词到底是不是敏感词」，否则只能凭感觉判断），
 * 写接口只给管理员——词典是平台级配置，改错一个词会让全站发布
 * 同时失效，这个风险不该给到客服。
 * <p>
 * 拦截器那层还有一道「客服只读」的兜底：即便前端按钮被绕过，
 * 客服的 POST/PUT/DELETE 也会在这里被 403 掉。
 */
@Tag(name = "敏感词管理")
@RestController
@RequestMapping("/api/sensitive-word")
@RequiredArgsConstructor
public class SensitiveWordController {

    private final SensitiveWordService sensitiveWordService;

    @Operation(summary = "敏感词分页列表（按类型Tab / 状态 / 关键词筛选）")
    @GetMapping("/page")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    public Result<IPage<SensitiveWordVO>> page(SensitiveWordQueryDTO query) {
        return Result.ok(sensitiveWordService.page(query));
    }

    @Operation(summary = "新增敏感词")
    @PostMapping
    @RequireRole(RoleConst.ADMIN)
    public Result<Long> create(@RequestBody @Valid SensitiveWordSaveDTO dto) {
        return Result.ok("敏感词已添加", sensitiveWordService.create(dto));
    }

    @Operation(summary = "编辑敏感词")
    @PutMapping("/{id}")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> update(@Parameter(description = "敏感词ID") @PathVariable Long id,
                               @RequestBody @Valid SensitiveWordSaveDTO dto) {
        sensitiveWordService.update(id, dto);
        return Result.ok("敏感词已更新", null);
    }

    @Operation(summary = "删除敏感词")
    @DeleteMapping("/{id}")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> delete(@Parameter(description = "敏感词ID") @PathVariable Long id) {
        sensitiveWordService.delete(id);
        return Result.ok("敏感词已删除", null);
    }

    @Operation(summary = "启用 / 禁用敏感词")
    @PutMapping("/{id}/status")
    @RequireRole(RoleConst.ADMIN)
    public Result<Void> updateStatus(@Parameter(description = "敏感词ID") @PathVariable Long id,
                                     @Parameter(description = "状态:0禁用 1启用")
                                     @RequestParam Integer status) {
        sensitiveWordService.updateStatus(id, status);
        return Result.ok(status != null && status == 1 ? "已启用" : "已禁用", null);
    }

    @Operation(summary = "批量添加（文本框一行一个）")
    @PostMapping("/batch")
    @RequireRole(RoleConst.ADMIN)
    public Result<SensitiveWordBatchVO> batchCreate(@RequestBody @Valid SensitiveWordBatchDTO dto) {
        SensitiveWordBatchVO result = sensitiveWordService.batchCreate(dto);
        return Result.ok("批量添加完成，新增 " + result.getInserted() + " 条", result);
    }

    @Operation(summary = "当前生效的词表（调试与词典体检用）")
    @GetMapping("/enabled")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    public Result<List<String>> enabledWords() {
        return Result.ok(sensitiveWordService.listEnabledWords());
    }

    @Operation(summary = "重新加载内存词典（多实例部署时手动同步）")
    @PostMapping("/reload")
    @RequireRole(RoleConst.ADMIN)
    public Result<Integer> reload() {
        int count = sensitiveWordService.reload();
        return Result.ok("词典已重新加载，当前 " + count + " 个启用词", count);
    }
}

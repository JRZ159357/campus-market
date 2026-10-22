// 文件路径: src/main/java/com/campus/market/controller/AddressController.java
package com.campus.market.controller;

import com.campus.market.common.Result;
import com.campus.market.dto.AddressSaveDTO;
import com.campus.market.service.AddressService;
import com.campus.market.util.UserContext;
import com.campus.market.vo.AddressVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 收货 / 见面地址接口。全部需要登录，服务层保证只能操作自己的地址。
 */
@Tag(name = "收货地址")
@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @Operation(summary = "我的地址列表（默认地址置顶）")
    @GetMapping("/list")
    public Result<List<AddressVO>> list() {
        return Result.ok(addressService.listOf(UserContext.getUserId()));
    }

    @Operation(summary = "新增地址（首个地址自动设为默认）")
    @PostMapping
    public Result<Long> create(@RequestBody @Valid AddressSaveDTO dto) {
        return Result.ok("地址已保存", addressService.create(UserContext.getUserId(), dto));
    }

    @Operation(summary = "修改地址")
    @PutMapping("/{id}")
    public Result<Void> update(@Parameter(description = "地址ID") @PathVariable Long id,
                               @RequestBody @Valid AddressSaveDTO dto) {
        addressService.update(UserContext.getUserId(), id, dto);
        return Result.ok("地址已更新", null);
    }

    @Operation(summary = "删除地址（删除默认地址后自动补选新的默认）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "地址ID") @PathVariable Long id) {
        addressService.delete(UserContext.getUserId(), id);
        return Result.ok("地址已删除", null);
    }

    @Operation(summary = "设为默认地址")
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@Parameter(description = "地址ID") @PathVariable Long id) {
        addressService.setDefault(UserContext.getUserId(), id);
        return Result.ok("已设为默认地址", null);
    }
}

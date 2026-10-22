// 文件路径: src/main/java/com/campus/market/controller/OperationLogController.java
package com.campus.market.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.common.Result;
import com.campus.market.common.RoleConst;
import com.campus.market.common.annotation.RequireRole;
import com.campus.market.dto.OperationLogQueryDTO;
import com.campus.market.service.OperationLogService;
import com.campus.market.vo.OperationLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志接口（只读）。
 * <p>
 * 这个 Controller 只有<b>一个</b>方法，而且是 GET。
 * <p>
 * 操作日志的价值全部来自「不可篡改」：一旦提供修改或删除入口，
 * 管理员在纠纷里删掉自己那几条记录，日志就失去了意义，
 * 答辩时被问「你怎么证明你没有偷偷改数据」也答不上来。
 * 所以这里不提供任何写接口——不是靠前端隐藏按钮，而是接口层根本不存在。
 * <p>
 * 权限：管理员与客服都能查（客服排查纠纷时需要知道某商品此前被谁下架过），
 * 底层拦截器的客服只读规则对 GET 不生效，因此客服看到的就是完整日志。
 */
@Tag(name = "操作日志")
@RestController
@RequestMapping("/api/operation-log")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    @Operation(summary = "操作日志分页（管理员与客服，只读）")
    @RequireRole({RoleConst.ADMIN, RoleConst.CUSTOMER_SERVICE})
    @GetMapping("/page")
    public Result<IPage<OperationLogVO>> page(OperationLogQueryDTO query) {
        return Result.ok(operationLogService.page(query));
    }
}
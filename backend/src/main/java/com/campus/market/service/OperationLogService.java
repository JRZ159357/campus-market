// 文件路径: src/main/java/com/campus/market/service/OperationLogService.java
package com.campus.market.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.market.dto.OperationLogQueryDTO;
import com.campus.market.entity.SysOperationLog;
import com.campus.market.vo.OperationLogVO;

/**
 * 操作日志业务接口。
 * <p>
 * 只有 {@link #record} 一个写方法，且它<b>不允许抛异常到调用方</b>：
 * 日志是事后追溯用的辅助信息，日志写失败绝不能连带把「封禁违规用户」
 * 「强制下架违规商品」这些安全操作回滚。因此 {@code record} 内部吞掉所有异常。
 */
public interface OperationLogService extends IService<SysOperationLog> {

    /**
     * 写一条操作日志。永不抛异常。
     *
     * @param operatorId 操作人，未登录（系统内部触发）传 null
     * @param module     模块码，见 OpLogModule
     * @param action     动作码，见 OpLogModule
     * @param targetId   操作目标ID，可为 null
     * @param detail     可读描述，可为 null
     */
    void record(Long operatorId, String module, String action, Long targetId, String detail);

    /** 操作日志分页（管理端只读） */
    IPage<OperationLogVO> page(OperationLogQueryDTO query);
}
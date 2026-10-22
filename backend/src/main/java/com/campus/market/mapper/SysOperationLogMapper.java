// 文件路径: src/main/java/com/campus/market/mapper/SysOperationLogMapper.java
package com.campus.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.market.entity.SysOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper。只有列表查询，没有更新与删除——
 * 日志的不可变性由「接口层根本不提供写方法」来保证，
 * 而不是靠约定：只要 Mapper 是 BaseMapper，理论上 updateById 是可用的，
 * 所以真正的防线在 Service 与 Controller 层都不提供修改入口。
 */
@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLog> {
}
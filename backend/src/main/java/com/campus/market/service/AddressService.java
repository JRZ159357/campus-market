// 文件路径: src/main/java/com/campus/market/service/AddressService.java
package com.campus.market.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.market.dto.AddressSaveDTO;
import com.campus.market.entity.Address;
import com.campus.market.vo.AddressVO;

import java.util.List;

/**
 * 收货 / 见面地址业务接口。
 * 所有方法都以 userId 为第一参数，从服务层保证"只能操作自己的地址"。
 */
public interface AddressService extends IService<Address> {

    /**
     * 我的地址列表：默认地址置顶，其余按创建时间倒序。
     */
    List<AddressVO> listOf(Long userId);

    /**
     * 新增地址，返回地址ID。首个地址自动成为默认地址。
     */
    Long create(Long userId, AddressSaveDTO dto);

    /**
     * 修改地址。
     */
    void update(Long userId, Long id, AddressSaveDTO dto);

    /**
     * 删除地址。删除默认地址后自动把最新的一条提升为默认。
     */
    void delete(Long userId, Long id);

    /**
     * 设为默认地址（同一用户其余地址自动取消默认）。
     */
    void setDefault(Long userId, Long id);

    /**
     * 取属于该用户的地址，不存在或不属于该用户时抛业务异常。
     */
    Address getOwned(Long userId, Long id);
}

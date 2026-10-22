// 文件路径: src/main/java/com/campus/market/service/impl/AddressServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.market.common.BusinessException;
import com.campus.market.dto.AddressSaveDTO;
import com.campus.market.entity.Address;
import com.campus.market.mapper.AddressMapper;
import com.campus.market.service.AddressService;
import com.campus.market.vo.AddressVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 地址业务实现。
 * <p>
 * "默认地址唯一"由服务层保证而不是数据库唯一索引：
 * 唯一索引只能约束"每个用户至多一条 is_default=1"，
 * 但切换默认时要先把旧的置 0 再置新，两步之间会短暂违反约束。
 * 这里统一走"先清空该用户所有默认 → 再设置目标"，放在同一事务内完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressServiceImpl extends ServiceImpl<AddressMapper, Address> implements AddressService {

    /** 单用户地址上限，防止把地址表当草稿本用 */
    private static final int MAX_ADDRESS = 20;

    @Override
    public List<AddressVO> listOf(Long userId) {
        List<Address> list = this.lambdaQuery()
                .eq(Address::getUserId, userId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getId)
                .list();
        return list.stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long userId, AddressSaveDTO dto) {
        long count = this.lambdaQuery().eq(Address::getUserId, userId).count();
        if (count >= MAX_ADDRESS) {
            throw new BusinessException("最多保存 " + MAX_ADDRESS + " 个地址，请先删除不用的");
        }
        // 第一条地址无论是否勾选默认，都设为默认，否则下单页会没有默认地址可选
        boolean asDefault = Boolean.TRUE.equals(dto.getIsDefault()) || count == 0;

        Address entity = new Address();
        entity.setUserId(userId);
        entity.setReceiverName(dto.getReceiverName().trim());
        entity.setPhone(dto.getPhone().trim());
        entity.setAddress(dto.getAddress().trim());
        entity.setIsDefault(0);
        this.save(entity);

        if (asDefault) {
            applyDefault(userId, entity.getId());
        }
        log.info("新增地址: userId={}, addressId={}", userId, entity.getId());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long userId, Long id, AddressSaveDTO dto) {
        Address entity = getOwned(userId, id);
        entity.setReceiverName(dto.getReceiverName().trim());
        entity.setPhone(dto.getPhone().trim());
        entity.setAddress(dto.getAddress().trim());
        this.updateById(entity);

        if (Boolean.TRUE.equals(dto.getIsDefault()) && !Integer.valueOf(1).equals(entity.getIsDefault())) {
            applyDefault(userId, id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        Address entity = getOwned(userId, id);
        this.removeById(id);
        // 删掉的是默认地址：把剩下最新的一条补上默认，保证下单页始终有默认项
        if (Integer.valueOf(1).equals(entity.getIsDefault())) {
            Address next = this.lambdaQuery()
                    .eq(Address::getUserId, userId)
                    .orderByDesc(Address::getId)
                    .last("limit 1")
                    .one();
            if (next != null) {
                applyDefault(userId, next.getId());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long id) {
        getOwned(userId, id);
        applyDefault(userId, id);
    }

    @Override
    public Address getOwned(Long userId, Long id) {
        Address entity = this.getById(id);
        if (entity == null || !userId.equals(entity.getUserId())) {
            throw new BusinessException("地址不存在或已被删除");
        }
        return entity;
    }

    /**
     * 先把该用户所有地址置为非默认，再把目标地址置为默认。
     * 两条 UPDATE 在同一事务内，其他事务看不到"没有默认地址"的中间态。
     */
    private void applyDefault(Long userId, Long addressId) {
        this.update(Wrappers.<Address>lambdaUpdate()
                .eq(Address::getUserId, userId)
                .ne(Address::getId, addressId)
                .set(Address::getIsDefault, 0));
        this.update(Wrappers.<Address>lambdaUpdate()
                .eq(Address::getId, addressId)
                .set(Address::getIsDefault, 1));
    }

    private AddressVO toVO(Address entity) {
        AddressVO vo = new AddressVO();
        vo.setId(entity.getId());
        vo.setReceiverName(entity.getReceiverName());
        vo.setPhone(entity.getPhone());
        vo.setAddress(entity.getAddress());
        vo.setIsDefault(Integer.valueOf(1).equals(entity.getIsDefault()));
        return vo;
    }
}

// 文件路径: src/main/java/com/campus/market/service/AdminAccountService.java
package com.campus.market.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.market.dto.AdminAccountPasswordDTO;
import com.campus.market.dto.AdminAccountQueryDTO;
import com.campus.market.dto.AdminAccountSaveDTO;
import com.campus.market.vo.AdminAccountVO;

/**
 * 后台账号管理服务（模块 11）。
 * <p>
 * <b>与 {@link AdminUserService} 的分工</b>：那一个管的是全平台用户
 * （含小程序端的 user / student），关注的是「这个用户发的东西合不合规」；
 * 这一个只管后台账号（admin / customer_service），关注的是
 * 「谁能进管理端、能干什么」。两者的约束完全不同——
 * 用户被封禁只是不能交易，后台账号被禁用则意味着一个运营岗位失效，
 * 因此后者多了「至少保留一个可用管理员」「不能操作自己」这类护栏。
 * <p>
 * <b>为什么单独建一个 Service 而不是往 UserService 上加方法</b>：
 * 账号管理里每一个写操作都要先做「会不会把系统锁死」的判断，
 * 这些判断和用户侧的资料修改完全是两套逻辑，混在一起后
 * 某天给用户改昵称时误走了账号管理的方法，就会出现
 * 「普通用户被当成后台账号改了角色」的事故。
 */
public interface AdminAccountService {

    /**
     * 后台账号分页。只包含 admin 与 customer_service 两种角色。
     */
    IPage<AdminAccountVO> page(AdminAccountQueryDTO query, Long operatorId);

    /**
     * 新增后台账号。账号唯一，密码 BCrypt 加密存储。
     *
     * @return 新账号ID
     */
    Long create(AdminAccountSaveDTO dto);

    /**
     * 编辑账号（昵称 / 角色 / 状态）。账号本身不可改。
     *
     * @param operatorId 当前操作人，用于「不能改自己的角色」判断
     */
    void update(Long id, AdminAccountSaveDTO dto, Long operatorId);

    /**
     * 重置密码。不允许重置自己的密码——
     * 自助改密需要校验旧密码，绕过旧密码校验等于把「改密」的
     * 安全前提拿掉了，而管理员本可以用「修改密码」功能完成同样的事。
     */
    void resetPassword(Long id, AdminAccountPasswordDTO dto, Long operatorId);

    /**
     * 启用 / 禁用。不能禁用自己，也不能禁用最后一个可用管理员。
     */
    void updateStatus(Long id, Integer status, Long operatorId);

    /**
     * 删除账号（逻辑删除）。不能删自己，也不能删掉最后一个管理员。
     * 删除时会释放原账号名，避免唯一索引挡住同名账号的重新建立。
     */
    void delete(Long id, Long operatorId);
}

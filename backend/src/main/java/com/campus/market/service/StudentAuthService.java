// 文件路径: src/main/java/com/campus/market/service/StudentAuthService.java
package com.campus.market.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.market.dto.StudentAuthApplyDTO;
import com.campus.market.dto.StudentAuthAuditDTO;
import com.campus.market.dto.StudentAuthQueryDTO;
import com.campus.market.entity.StudentAuth;
import com.campus.market.vo.AuthStatusVO;
import com.campus.market.vo.StudentAuthVO;

/**
 * 学生认证业务接口。
 */
public interface StudentAuthService extends IService<StudentAuth> {

    /**
     * 查询当前用户的认证状态（含最近一次申请记录）。
     */
    AuthStatusVO myStatus(Long userId);

    /**
     * 提交认证申请。校验：未认证、无待审核、学号未被占用。
     */
    void apply(Long userId, StudentAuthApplyDTO dto);

    /**
     * 管理端分页查询认证申请。
     */
    IPage<StudentAuthVO> pageQuery(StudentAuthQueryDTO query);

    /**
     * 管理端查看申请详情。
     */
    StudentAuthVO detail(Long id);

    /**
     * 审核。通过后用户角色升级为 student 并赋予发布上限。
     */
    void audit(Long authId, Long auditorId, StudentAuthAuditDTO dto);
}

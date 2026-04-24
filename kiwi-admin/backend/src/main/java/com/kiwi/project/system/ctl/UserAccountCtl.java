package com.kiwi.project.system.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.kiwi.framework.security.PasswordService;
import com.kiwi.project.system.dao.SysUserDao;
import com.kiwi.project.system.entity.SysUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 与前端 {@code AccountService} 对齐：{@code PUT /user/psd} 修改密码；{@code PUT /user/update} 更新基本资料。
 */
@Tag(name = "账户")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserAccountCtl {

    private final SysUserDao sysUserDao;
    private final PasswordService passwordService;

    @PutMapping("/psd")
    @SaCheckLogin
    @Operation(summary = "修改密码（仅允许修改当前登录用户）")
    public void changePassword(@RequestBody ChangePasswordRequest body) {
        if (body == null || StringUtils.isAnyBlank(body.getOldPassword(), body.getNewPassword())) {
            throw new RuntimeException("原密码与新密码不能为空");
        }
        String loginId = StpUtil.getLoginId().toString();
        if (StringUtils.isBlank(body.getId()) || !loginId.equals(body.getId())) {
            throw new RuntimeException("只能修改当前登录用户密码");
        }
        if (body.getNewPassword().length() < 6) {
            throw new RuntimeException("新密码长度至少 6 位");
        }
        SysUser user = sysUserDao.findById(loginId).orElseThrow(() -> new RuntimeException("用户不存在"));
        String oldHash = passwordService.encodePassword(body.getOldPassword());
        if (!oldHash.equals(user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }
        user.setPassword(passwordService.encodePassword(body.getNewPassword()));
        sysUserDao.updateSelective(user);
    }

    @PutMapping("/update")
    @SaCheckLogin
    @Operation(summary = "更新基本资料（仅允许修改当前登录用户，字段与 SysUser 一致）")
    public void updateProfile(@RequestBody UpdateProfileRequest body) {
        if (body == null) {
            throw new RuntimeException("请求体不能为空");
        }
        String loginId = StpUtil.getLoginId().toString();
        SysUser user = sysUserDao.findById(loginId).orElseThrow(() -> new RuntimeException("用户不存在"));
        if (StringUtils.isBlank(body.getEmail())) {
            throw new RuntimeException("邮箱不能为空");
        }
        String sex = StringUtils.trimToNull(body.getSex());
        if (sex != null && !Set.of("0", "1", "2").contains(sex)) {
            throw new RuntimeException("性别取值无效");
        }
        user.setNickName(StringUtils.trimToNull(body.getNickName()));
        user.setEmail(StringUtils.trimToNull(body.getEmail()));
        user.setPhonenumber(StringUtils.trimToNull(body.getPhonenumber()));
        user.setSex(sex);
        user.setAvatar(StringUtils.trimToNull(body.getAvatar()));
        sysUserDao.updateSelective(user);
    }

    @Data
    public static class ChangePasswordRequest {
        private String id;
        private String oldPassword;
        private String newPassword;
    }

    @Data
    public static class UpdateProfileRequest {
        /** 用户昵称 */
        private String nickName;
        /** 用户邮箱 */
        private String email;
        /** 手机号码，与实体字段 phonenumber 一致 */
        private String phonenumber;
        /** 0 男 1 女 2 未知 */
        private String sex;
        /** 头像地址或 URL */
        private String avatar;
    }
}

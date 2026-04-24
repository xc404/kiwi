package com.kiwi.project.system.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.kiwi.framework.security.PasswordService;
import com.kiwi.project.bpm.integration.IntegrationDevice;
import com.kiwi.project.bpm.integration.KiwiIntegrationProperties;
import com.kiwi.project.system.dao.SysUserDao;
import com.kiwi.project.system.entity.SysUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 与前端 {@code AccountService} 中 {@code PUT /user/psd} 对齐：当前登录用户修改自己的密码。
 */
@Tag(name = "账户")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserAccountCtl {

    private final SysUserDao sysUserDao;
    private final PasswordService passwordService;
    private final KiwiIntegrationProperties integrationProperties;

    /**
     * 为当前用户创建（并轮换）机机集成用长期 Token，写入 Sa-Token 独立终端 {@link IntegrationDevice#TYPE}。
     */
    @PostMapping("/integration-api-token")
    @SaCheckLogin
    @Operation(summary = "签发机机集成长期访问令牌（cryoEMS 等使用 Authorization: Bearer）")
    public IntegrationApiTokenVo issueIntegrationApiToken() {
        Object loginId = StpUtil.getLoginId();
        long ttl = integrationProperties.getApiTokenTimeoutSeconds();
        if (ttl <= 0) {
            ttl = 60L * 60 * 24 * 365;
        }
        StpUtil.logout(loginId, IntegrationDevice.TYPE);
        SaLoginParameter param = SaLoginParameter.create()
                .setDevice(IntegrationDevice.TYPE)
                .setTimeout(ttl)
                .setIsWriteHeader(false);
        String token = StpUtil.createLoginSession(loginId, param);
        return IntegrationApiTokenVo.of(token, ttl);
    }

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

    @Data
    public static class ChangePasswordRequest {
        private String id;
        private String oldPassword;
        private String newPassword;
    }

    @Data
    @AllArgsConstructor
    public static class IntegrationApiTokenVo {
        /** Sa-Token 原始 token 值（请求头格式为 {@code Authorization: Bearer <token>}） */
        private String token;
        /** 与全局 sa-token 配置一致，固定为 Bearer */
        private String tokenType;
        /** 与本次签发一致的过期时间（秒） */
        private long expiresInSeconds;

        static IntegrationApiTokenVo of(String token, long expiresInSeconds) {
            return new IntegrationApiTokenVo(token, "Bearer", expiresInSeconds);
        }
    }
}

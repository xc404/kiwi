package com.kiwi.framework.session;

import com.kiwi.project.system.entity.SysRole;
import com.kiwi.project.system.entity.SysUser;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Getter
@NoArgsConstructor
public class SessionUser
{

    private String id;
    private String deptId;
    /**
     * 用户账号
     */
    private String userName;
    /**
     * 用户昵称
     */
    private String nickName;
    /**
     * 用户邮箱
     */
    private String email;
    /**
     * 手机号码
     */
    private String phonenumber;
    /**
     * 用户性别
     */
    private String sex;
    /**
     * 用户头像
     */
    private String avatar;

    /**
     * 最后登录IP
     */
    private String loginIp;

    /**
     * 最后登录时间
     */
    private Date loginDate;

    private List<String> roleIds;


    public List<String> getRoleIds() {
        return Optional.ofNullable(roleIds).orElse(new ArrayList<>());
    }


    public boolean isSuperUser() {
        return this.getRoleIds().stream().anyMatch(r -> r.equals(SysRole.Admin));
    }

    public SessionUser(SysUser sysUser) {
        this.id = sysUser.getId();
        this.deptId = sysUser.getDeptId();
        this.userName = sysUser.getUserName();
        this.nickName = sysUser.getNickName();
        this.email = sysUser.getEmail();
        this.phonenumber = sysUser.getPhonenumber();
        this.sex = sysUser.getSex();
        this.avatar = sysUser.getAvatar();
        this.loginIp = sysUser.getLoginIp();
        this.loginDate = sysUser.getLoginDate();
        this.roleIds = sysUser.getRoleIds();
    }


}

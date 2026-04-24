package com.kiwi.project.system.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.kiwi.project.system.entity.SysPersonalAccessToken;
import com.kiwi.project.system.service.PersonalAccessTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * 个人长期访问令牌：持久化 + Sa-Token 多端终端，可列表与主动吊销。
 */
@Tag(name = "个人访问令牌")
@RestController
@RequestMapping("/user/personal-access-tokens")
@RequiredArgsConstructor
public class PersonalAccessTokenCtl {

    private final PersonalAccessTokenService personalAccessTokenService;

    @GetMapping
    @SaCheckLogin
    @Operation(summary = "列出当前用户的长期访问令牌")
    public List<PersonalAccessTokenListItem> listMine() {
        String userId = StpUtil.getLoginId().toString();
        return personalAccessTokenService.listMine(userId).stream().map(PersonalAccessTokenListItem::from).toList();
    }

    @PostMapping
    @SaCheckLogin
    @Operation(summary = "新建长期访问令牌（明文仅返回一次）")
    public CreatePersonalAccessTokenResponse create(@RequestBody(required = false) CreatePersonalAccessTokenRequest body) {
        String userId = StpUtil.getLoginId().toString();
        String name = body != null ? body.getName() : null;
        PersonalAccessTokenService.CreateResult r = personalAccessTokenService.create(userId, name);
        return CreatePersonalAccessTokenResponse.from(r);
    }

    @DeleteMapping("{id}")
    @SaCheckLogin
    @Operation(summary = "删除并吊销指定长期访问令牌")
    public void revoke(@PathVariable String id) {
        personalAccessTokenService.revoke(StpUtil.getLoginId().toString(), id);
    }

    @Data
    public static class CreatePersonalAccessTokenRequest {
        private String name;
    }

    @Data
    @AllArgsConstructor
    public static class PersonalAccessTokenListItem {
        private String id;
        private String name;
        private String tokenHint;
        private Date createdTime;
        private Date expiresAt;

        static PersonalAccessTokenListItem from(SysPersonalAccessToken e) {
            return new PersonalAccessTokenListItem(
                    e.getId(),
                    e.getName(),
                    e.getTokenHint(),
                    e.getCreatedTime(),
                    e.getExpiresAt());
        }
    }

    @Data
    @AllArgsConstructor
    public static class CreatePersonalAccessTokenResponse {
        private String id;
        private String token;
        private String tokenType;
        private long expiresInSeconds;
        private Date expiresAt;

        static CreatePersonalAccessTokenResponse from(PersonalAccessTokenService.CreateResult r) {
            return new CreatePersonalAccessTokenResponse(
                    r.getId(),
                    r.getToken(),
                    "Bearer",
                    r.getExpiresInSeconds(),
                    r.getExpiresAt());
        }
    }
}

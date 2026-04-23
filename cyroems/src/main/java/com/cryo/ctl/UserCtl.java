package com.cryo.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.cryo.common.utils.CacheUtils;
import com.cryo.dao.UserRepository;
import com.cryo.model.user.Role;
import com.cryo.model.user.User;
import com.cryo.oauth.ctl.OAuthResult;
import com.cryo.service.JwtService;
import com.cryo.service.PasswordService;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.service.cmd.UserSpace;
import com.cryo.service.cryosparc.CryosparcClient;
import com.cryo.service.mail.MailService;
import com.cryo.service.session.SessionService;
import com.cryo.service.session.SessionUser;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.core.result.R;
import net.dreamlu.mica.core.utils.JsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.text.MessageFormat;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class UserCtl
{
    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final MailService mailService;
    private final SoftwareService softwareService;
    private final UserSpace userSpace;
    private final CryosparcClient cryosparcClient;
    @Value("${app.frontend.url}")
    private String frontend_url;
    @Value("${app.test.enabled}")
    private boolean testMode;

    @PostMapping("/api/register")
    @ResponseBody
    public R<Void> sendRegisterEmail(@RequestBody UserRegisterInput input) {
        if( !input.email.contains("@") ) {
            input.email = input.email + "@shanghaitech.edu.cn";
        }
        User user = createUser(input);
        if( user.getIs_verified() ) {
            throw new RuntimeException("User exist");
        }
        Map<String, Object> map = JsonUtil.convertValue(user, Map.class);
        String token = this.jwtService.sign(JwtService.JwtType.register, map);
        String verification_url = MessageFormat.format("{0}/register?token={1}", frontend_url, token);
        String body = MessageFormat.format("Please click the following link to verify your email address: {0}\nThis link will expire in 7 days.", verification_url);
        this.mailService.sendMail(user.getEmail(), "EM Server Email Verification", body);
        return R.success();
    }


    @PostMapping("/api/v2/register")
    @ResponseBody
    public SessionCtl.LoginResult registerV2(@RequestBody UserRegisterInput input) {
        if( !input.email.contains("@") ) {
            input.email = input.email + "@shanghaitech.edu.cn";
        }

//        if( user.getIs_verified() ) {
//            throw new RuntimeException("User exist");
//        }
        OAuthResult oAuthResult = CacheUtils.get(input.token);
        if( oAuthResult == null ) {
            throw new RuntimeException("Token expired");
        }
        if( !input.getEmail().equals(oAuthResult.getAuthUser().getEmail()) ) {
            throw new RuntimeException("Email not match");
        }
        User user = createUser(input);
        log.info("Register user: {}", user);
        log.info("bind oauth user " + oAuthResult.getAuthUser().getUuid() + " to user " + user.getId());
        user.addOAuthUser(oAuthResult.getPlatform(), oAuthResult.getAuthUser());
        user.setIs_verified(true);
        user.setEmail(oAuthResult.getAuthUser().getEmail());
        user.setName(oAuthResult.getAuthUser().getUsername());
        this.userRepository.save(user);
        StpUtil.login(user.getId());
        return new SessionCtl.LoginResult(SessionCtl.LoginAction.LoginSuccess, StpUtil.getTokenValue());
    }


    private User createUser(UserRegisterInput input) {
        User user = this.userRepository.findByEmail(input.email).orElse(null);
        if( user != null ) {
            throw new RuntimeException("User exist");
        }
        String name = input.email.substring(0, input.email.indexOf("@"));
        String group = input.group;
        if( !testMode || (StringUtils.isBlank(input.group) && StringUtils.isNotBlank(input.ssh_username)) ) {
            group = checkUser(input.getSsh_username());
            try {
                this.softwareService.checkPassword(input.getSsh_username(), input.getSsh_password());
            } catch( Exception e ) {
                throw new RuntimeException("Invalid ssh password");
            }
        }
        User newUser = new User();
        newUser.setEmail(input.email);
//            newUser.setFirst_name(input.first_name);
//            newUser.setLast_name(input.last_name);
        newUser.setUser_group(group);
        newUser.setRole(Role.NORMAL);
//            newUser.setName(name);
        newUser.setSys_username(input.getSsh_username());
        newUser.setPassword(passwordService.encodePassword(input.password));
        newUser.setIs_verified(false);
        if( StringUtils.isNotBlank(input.getSsh_username()) ) {

            newUser.setDefault_dir("/home/" + input.getSsh_username());
        } else {
            newUser.setDefault_dir("/home/" + name);
        }
        user = newUser;
        this.userRepository.save(newUser);
        return user;
    }

    private String checkUser(String name) {
        SoftwareService.CmdProcess group = this.softwareService.group(name);
        group.startAndWait();
        String result = group.result();
        if( result.contains("no such user") ) {
            throw new RuntimeException(result);
        }
        String groupName = result.split(":")[1];
        return StringUtils.trim(groupName).split(" +")[0];

    }

    @PostMapping("/api/verify_email")
    @ResponseBody
    public SessionUser verifyEmail(@RequestBody TokenInput input) {
        Map<String, Object> verify = this.jwtService.verify(JwtService.JwtType.register, input.token);
        User user = this.userRepository.findById(verify.get("id").toString()).orElse(null);
        if( user == null ) {
            throw new RuntimeException("Invalid token");
        }
        if( !user.getIs_verified() ) {
            user.setIs_verified(true);
            this.userRepository.save(user);
        }
        StpUtil.login(user.getId());
        return new SessionUser(user, StpUtil.getTokenValue());
    }

    @PostMapping("/api/forgot_password")
    @ResponseBody
    @SecurityRequirements
    public R forgotPassword(@RequestBody ForgotPasswordInput input) {
        User user = this.userRepository.findByEmail(input.email).orElse(null);
        if( user == null ) {
            throw new RuntimeException("User not exist");
        }
        Map<String, Object> map = JsonUtil.convertValue(user, Map.class);
        String token = this.jwtService.sign(JwtService.JwtType.password_reset, map);
        String reset_url = MessageFormat.format("{0}/reset-password?token={1}", frontend_url, token);
        String body = MessageFormat.format("Please click the following link to reset your password: {0}\nThis link will expire 15 minutes.", reset_url);
        this.mailService.sendMail(user.getEmail(), "EM Server Password Reset", body);
        return R.success();
    }

    @PostMapping("/api/reset_password")
    @ResponseBody
    @SecurityRequirements
    public SessionUser resetPassword(@RequestBody ResetPasswordInput input) {
        Map<String, Object> verify = this.jwtService.verify(JwtService.JwtType.password_reset, input.token);
        User user = this.userRepository.findById(verify.get("id").toString()).orElse(null);
        if( user == null ) {
            throw new RuntimeException("Invalid token");
        }
        user.setPassword(passwordService.encodePassword(input.new_password));
        if( !user.getIs_verified() ) {
            user.setIs_verified(true);
        }
        this.userRepository.save(user);
        StpUtil.login(user.getId());
        return new SessionUser(user, StpUtil.getTokenValue());
    }

//    @GetMapping("/api/users")
//    @ResponseBody
//    @SaCheckLogin
//    public List<User> getUsers() {
//        SessionUser sessionUser = sessionService.getSessionUser();
//        Role role = sessionUser.getUser().getRole();
//        switch( role ) {
//            case normal:
//                return List.of();
//            case group_admin:
//                return this.userRepository.findByQuery(Query.query(Criteria.where("user_group").is(sessionUser.getUser().getUser_group())));
//            case admin, viewer:
//                return this.userRepository.findAll();
//            default:
//                throw new RuntimeException("Invalid user role");
//        }
//    }


    @GetMapping("/api/user/used_quota")
    @ResponseBody
    @SaCheckLogin
    public SpaceOutput get_used_quota() {
        SessionUser sessionUser = sessionService.getSessionUser();
        long space = userSpace.getUserSpace(sessionUser.getUser().getSys_username());
        return new SpaceOutput(space);
    }


    @SaCheckLogin
    @RequestMapping(value = "/bind/cryosparc", method = RequestMethod.GET)
    @ResponseBody
    public void bindCryosparc(@RequestBody BindSparcUser bindSparcUser) {

        String userId = this.cryosparcClient.findUser(bindSparcUser.getEmail(), bindSparcUser.getPassword());
        if( StringUtils.isBlank(userId) ) {
            throw new RuntimeException("Invalid cryosparc username or password");
        }
        SessionUser sessionUser = this.sessionService.getSessionUser();
        User user = sessionUser.getUser();
        user.setCryosparcUserId(userId);
        this.userRepository.save(user);

    }

    @Data
    public static class BindSparcUser
    {

        private String email;
        private String password;
    }

    @Data
    public static class UserRegisterInput
    {
        @JsonProperty("email")
        String email;
        @JsonProperty("password")
        String password;
        @JsonProperty("first_name")
        String first_name;
        @JsonProperty("last_name")
        String last_name;
        //        @Deprecated
        @JsonProperty("ssh_username")
        String ssh_username;
        @Deprecated
        @JsonProperty("ssh_password")
        String ssh_password;
        @JsonProperty("group")
        String group;

        @JsonProperty("token")
        private String token;
    }

    @Data
    public static class TokenInput
    {
        @JsonProperty("token")
        private String token;
    }

    @Data
    public static class ForgotPasswordInput
    {
        @JsonProperty("email")
        String email;

    }

    @Data
    public static class ResetPasswordInput
    {
        @JsonProperty("token")
        String token;
        @JsonProperty("new_password")
        String new_password;
    }

    @Data
    public static class SpaceOutput
    {
        private final long available;

        public SpaceOutput(long available) {
            this.available = available;
        }
    }

}

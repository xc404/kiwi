//package com.cryo.ctl;
//
//import com.cryo.service.mail.MailService;
//import lombok.RequiredArgsConstructor;
//import net.dreamlu.mica.core.result.R;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.stereotype.Controller;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.ResponseBody;
//
//@Controller
//@RequiredArgsConstructor
//public class MailCtl {
//
//    private final MailService mailService;
//
//
//
//    @PostMapping("/api/mail/test")
//    @ResponseBody
//    @ConditionalOnProperty(value = "app.test.enabled",havingValue = "true", matchIfMissing = false)
//    public R sendMail(@RequestBody SendMailInput input) {
//        mailService.sendMail(input.to,input.subject,input.text);
//        return R.success();
//    }
//
//    public static class SendMailInput {
//        public String to;
//        public String text;
//        public String subject;
//    }
//}

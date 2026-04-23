package com.cryo.service.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService implements InitializingBean
{
    @Value("${app.mailer.sender}")
    private String defaultMailerSender = "cryoem@cryoem.com";
    private final String resourcePath = "mail/";
    private Map<MailTemplate, String> mailTemplates = new HashMap<>();
    @Value("${app.mailer.cc}")
    private String[] cc;
    private final MailSender mailSender;

    public void sendMail(String to, String subject, String text) {
        var message = new SimpleMailMessage();
        try {
            message.setTo(to);
            message.setFrom(defaultMailerSender);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch( Exception e ) {
            log.error(e.getMessage(), e);
        }
    }


    public void sendMail(String to, String subject, MailTemplate template, Map<String, Object> context) {
        var message = new SimpleMailMessage();
        try {
            message.setTo(to);
            message.setFrom(defaultMailerSender);
            message.setCc(cc);
            message.setSubject(subject);
            var text = MailTemplateRenderer.render(mailTemplates.get(template), context);
            message.setText(text);
            mailSender.send(message);
        } catch( Exception e ) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {

        MailTemplate[] values = MailTemplate.values();
        for( MailTemplate value : values ) {
            String resource = resourcePath + value.name().toLowerCase() + ".tpl";
            mailTemplates.put(value, resource);
        }
    }
}

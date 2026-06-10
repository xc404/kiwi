package com.kiwi.bpmn.component.notification;

import com.kiwi.bpmn.core.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

@ComponentDescription(
        name = "发送邮件",
        group = "通知",
        version = "1.0",
        description = "通过 SMTP 发送纯文本邮件；账号密码建议用项目环境变量 ${SMTP_USER} / ${SMTP_PASSWORD}",
        inputs = {
                @ComponentParameter(key = "smtp_host", description = "SMTP 主机", required = true),
                @ComponentParameter(
                        key = "smtp_port",
                        description = "SMTP 端口",
                        required = true,
                        schema = @Schema(defaultValue = "587")),
                @ComponentParameter(key = "smtp_user", description = "SMTP 用户名"),
                @ComponentParameter(key = "smtp_password", description = "SMTP 密码"),
                @ComponentParameter(
                        key = "smtp_starttls",
                        description = "是否启用 STARTTLS",
                        htmlType = "CheckBox",
                        schema = @Schema(defaultValue = "true")),
                @ComponentParameter(key = "from", description = "发件人地址", required = true),
                @ComponentParameter(key = "to", description = "收件人（逗号分隔）", required = true),
                @ComponentParameter(key = "subject", description = "主题", required = true),
                @ComponentParameter(key = "body", description = "正文（纯文本）", required = true)
        })
@Component("emailSend")
public class EmailSendActivity implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws MessagingException {
        String host = ExecutionUtils.requireStringInputVariable(execution, "smtp_host");
        int port = ExecutionUtils.getIntInputVariable(execution, "smtp_port").orElse(587);
        String user = ExecutionUtils.getStringInputVariable(execution, "smtp_user").orElse(null);
        String password = ExecutionUtils.getStringInputVariable(execution, "smtp_password").orElse(null);
        boolean startTls =
                ExecutionUtils.getBooleanInputVariable(execution, "smtp_starttls").orElse(true);
        String from = ExecutionUtils.requireStringInputVariable(execution, "from");
        String to = ExecutionUtils.requireStringInputVariable(execution, "to");
        String subject = ExecutionUtils.requireStringInputVariable(execution, "subject");
        String body = ExecutionUtils.requireStringInputVariable(execution, "body");

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", String.valueOf(user != null && !user.isBlank()));
        props.put("mail.smtp.starttls.enable", String.valueOf(startTls));

        Session session;
        if (user != null && !user.isBlank()) {
            session =
                    Session.getInstance(
                            props,
                            new Authenticator() {
                                @Override
                                protected PasswordAuthentication getPasswordAuthentication() {
                                    return new PasswordAuthentication(user, password == null ? "" : password);
                                }
                            });
        } else {
            session = Session.getInstance(props);
        }

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        for (String addr : to.split(",")) {
            String trimmed = addr.trim();
            if (!trimmed.isEmpty()) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(trimmed));
            }
        }
        message.setSubject(subject, StandardCharsets.UTF_8.name());
        message.setText(body, StandardCharsets.UTF_8.name());
        Transport.send(message);
    }
}

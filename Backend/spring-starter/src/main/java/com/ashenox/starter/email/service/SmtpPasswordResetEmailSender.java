package com.ashenox.starter.email.service;

import com.ashenox.starter.auth.passwordreset.port.PasswordResetEmailSender;
import com.ashenox.starter.shared.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

import static com.ashenox.starter.email.config.EmailUtils.getPasswordResetUrl;

@Component
@RequiredArgsConstructor
public class SmtpPasswordResetEmailSender implements PasswordResetEmailSender {
    private static final String UTF_8 = "UTF-8";
    private static final String SUBJECT = "Restablecer contraseña";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AppProperties appProperties;

    @Override
    public void send(String recipient, String displayName, String token) {
        try {
            Context context = new Context();
            context.setVariables(Map.of(
                    "name", displayName,
                    "url", getPasswordResetUrl(appProperties.getMail().getFrontendBaseUrl().toString(), token)));
            String html = templateEngine.process("password-reset-template", context);
            var message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, UTF_8);
            helper.setPriority(1);
            helper.setSubject(SUBJECT);
            helper.setFrom(appProperties.getMail().getUsername());
            helper.setTo(recipient);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo entregar el correo de recuperación", exception);
        }
    }
}

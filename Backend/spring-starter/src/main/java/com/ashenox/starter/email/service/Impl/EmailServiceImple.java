package com.ashenox.starter.email.service.Impl;



import com.ashenox.starter.email.service.Interface.EmailService;
import com.ashenox.starter.shared.config.AppProperties;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.BodyPart;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;
import java.util.Map;

import static com.ashenox.starter.email.config.EmailUtils.*;


@Service
@RequiredArgsConstructor
public class EmailServiceImple implements EmailService {
    private static final String NEW_USER_ACCOUNT_VERIFICATION = "New User Account Verification";
    public static final String UTF_8_ENCODING = "UTF-8";
    public static final String EMAIL_TEMPLATE = "emailtemplate";
    public static final String TEXT_HTML_ENCONDING = "text/html";
    public static final String RESTABLECER_CONTRASEÑA = "Restablecer contraseña";

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImple.class);
    private final JavaMailSender emailSender;
    private final TemplateEngine templateEngine;
    private final AppProperties appProperties;
    @Override
    @Async   //Enviar Mensaje Simple
    public void sendSimpleMailMessage(String name, String to, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setSubject(NEW_USER_ACCOUNT_VERIFICATION);
            message.setFrom(fromEmail());
            message.setTo(to);
            message.setText(getEmailMessage(name, verificationBaseUrl(), token));
            emailSender.send(message);
        } catch (Exception exception) {
            throw new RuntimeException(exception.getMessage());
        }
    }

    @Override
    @Async // Con archivo Adjunto
    public void sendMimeMessageWithAttachments(String name, String to, String token) {
        try {
            MimeMessage message = getMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, UTF_8_ENCODING);
            helper.setPriority(1);
            helper.setSubject(NEW_USER_ACCOUNT_VERIFICATION);
            helper.setFrom(fromEmail());
            helper.setTo(to);
            helper.setText(getEmailMessage(name, verificationBaseUrl(), token));
            //Add attachments
            FileSystemResource fort = new FileSystemResource(new File(System.getProperty("user.home") + "/Downloads/images/fort.jpg"));
            FileSystemResource dog = new FileSystemResource(new File(System.getProperty("user.home") + "/Downloads/images/dog.jpg"));
            FileSystemResource homework = new FileSystemResource(new File(System.getProperty("user.home") + "/Downloads/images/homework.docx"));
            helper.addAttachment(fort.getFilename(), fort);
            helper.addAttachment(dog.getFilename(), dog);
            helper.addAttachment(homework.getFilename(), homework);
            emailSender.send(message);
        } catch (Exception exception) {

            throw new RuntimeException(exception.getMessage());
        }
    }


    @Override
    @Async // Archivos Incrustados, a diferencia de WithAttachments, las imagenes y archivos estan dentro de la redaccion del mail y no incrustados.
    public void sendMimeMessageWithEmbeddedFiles(String name, String to, String token) {
        try {
            MimeMessage message = getMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, UTF_8_ENCODING);
            helper.setPriority(1);
            helper.setSubject(NEW_USER_ACCOUNT_VERIFICATION);
            helper.setFrom(fromEmail());
            helper.setTo(to);
            helper.setText(getEmailMessage(name, verificationBaseUrl(), token));
            //Add attachments  agregamos archivos tanto imagenes como documentos
            FileSystemResource fort = new FileSystemResource(new File(System.getProperty("user.home") + "/Downloads/images/fort.jpg"));
            FileSystemResource dog = new FileSystemResource(new File(System.getProperty("user.home") + "/Downloads/images/dog.jpg"));
            FileSystemResource homework = new FileSystemResource(new File(System.getProperty("user.home") + "/Downloads/images/homework.docx"));
            helper.addInline(getContentId(fort.getFilename()), fort); //agregamos imagen getContentId--> para ponerlo entre < > como un html
            helper.addInline(getContentId(dog.getFilename()), dog); //agregamos imagen perro
            helper.addInline(getContentId(homework.getFilename()), homework); //agregamos documento
            emailSender.send(message);
        } catch (Exception exception) {

            throw new RuntimeException(exception.getMessage());
        }
    }

    @Override
    @Async //Enviar mensaje con html
    public void sendHtmlEmail(String name, String to, String token) {
        try {
            Context context = new Context();
            context.setVariable("name", name);
            context.setVariable("url", getVerificationUrl(verificationBaseUrl(), token));
            context.setVariables(Map.of("name", name, "url", getVerificationUrl(verificationBaseUrl(), token)));
            String text = templateEngine.process(EMAIL_TEMPLATE, context);
            MimeMessage message = getMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, UTF_8_ENCODING);
            helper.setPriority(1);
            helper.setSubject(NEW_USER_ACCOUNT_VERIFICATION);
            helper.setFrom(fromEmail());
            helper.setTo(to);
            helper.setText(text, true);
            //Add attachments (Optional)
            /*FileSystemResource fort = new FileSystemResource(new File(System.getProperty("user.home") + "/Downloads/images/fort.jpg"));
            FileSystemResource dog = new FileSystemResource(new File(System.getProperty("user.home") + "/Downloads/images/dog.jpg"));
            FileSystemResource homework = new FileSystemResource(new File(System.getProperty("user.home") + "/Downloads/images/homework.docx"));
            helper.addAttachment(fort.getFilename(), fort);
            helper.addAttachment(dog.getFilename(), dog);
            helper.addAttachment(homework.getFilename(), homework);*/
            emailSender.send(message);
        } catch (Exception exception) {

            throw new RuntimeException(exception.getMessage());
        }
    }

    @Override
    @Async //Enviar mensaje con html y archivos
    public void sendHtmlEmailWithEmbeddedFiles(String name, String to, String token) {
        try {
            MimeMessage message = getMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, UTF_8_ENCODING);
            helper.setPriority(1);
            helper.setSubject(NEW_USER_ACCOUNT_VERIFICATION);
            helper.setFrom(fromEmail());
            helper.setTo(to);
            //helper.setText("", true);
            Context context = new Context();
            context.setVariables(Map.of("name", name, "url", getVerificationUrl(verificationBaseUrl(), token)));
            String text = templateEngine.process(EMAIL_TEMPLATE, context);

            // Add HTML email body
            MimeMultipart mimeMultipart = new MimeMultipart("related");
            BodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setContent(text, TEXT_HTML_ENCONDING);
            mimeMultipart.addBodyPart(messageBodyPart);

            // Add images to the email body
            BodyPart imageBodyPart = new MimeBodyPart();
            DataSource dataSource = new FileDataSource(System.getProperty("usuario.soporte") + "/Descargas/imagen/Email.jpg");
            imageBodyPart.setDataHandler(new DataHandler(dataSource));
            imageBodyPart.setHeader("Content-ID", "image");
            mimeMultipart.addBodyPart(imageBodyPart);
            message.setContent(mimeMultipart);
            emailSender.send(message);
        } catch (Exception exception) {

            throw new RuntimeException(exception.getMessage());
        }
    }

    @Override
    @Async
    public void sendPasswordResetEmail(String name, String to, String token) {
        try {
            Context context = new Context();
            context.setVariables(Map.of("name", name, "url", getPasswordResetUrl(frontendBaseUrl(), token)));
            String html = templateEngine.process("password-reset-template", context); // Nuevo template
            MimeMessage message = getMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, UTF_8_ENCODING);
            helper.setPriority(1);
            helper.setSubject(RESTABLECER_CONTRASEÑA);
            helper.setFrom(fromEmail());
            helper.setTo(to);
            helper.setText(html, true);
            emailSender.send(message);
        } catch (Exception e) {
            
            throw new RuntimeException("Error al enviar email: " + e.getMessage());
        }
    }

    private MimeMessage getMimeMessage() {
        return emailSender.createMimeMessage();
    }

    private String fromEmail() {
        return appProperties.getMail().getUsername();
    }

    private String verificationBaseUrl() {
        return appProperties.getMail().getVerificationBaseUrl().toString();
    }

    private String frontendBaseUrl() {
        return appProperties.getMail().getFrontendBaseUrl().toString();
    }

    private String getContentId(String filename) {
        return "<" + filename + ">";
    }


}

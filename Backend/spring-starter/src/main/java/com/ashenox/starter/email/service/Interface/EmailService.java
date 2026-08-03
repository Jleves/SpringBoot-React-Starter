package com.ashenox.starter.email.service.Interface;


import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface EmailService {
    void sendSimpleMailMessage(String name, String to, String token);
    void sendMimeMessageWithAttachments(String name, String to, String token);

    void sendMimeMessageWithEmbeddedFiles(String name, String to, String token);
    void sendHtmlEmail(String name, String to, String token);
    void sendHtmlEmailWithEmbeddedFiles(String name, String to, String token);
    public void sendPasswordResetEmail(String name, String to, String token);

}

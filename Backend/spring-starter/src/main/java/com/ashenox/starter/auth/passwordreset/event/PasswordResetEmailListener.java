package com.ashenox.starter.auth.passwordreset.event;

import com.ashenox.starter.auth.passwordreset.port.PasswordResetEmailSender;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PasswordResetEmailListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetEmailListener.class);
    private final PasswordResetEmailSender emailSender;

    @Async("passwordResetEmailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(PasswordResetRequested event) {
        try {
            emailSender.send(event.recipient(), event.displayName(), event.token());
        } catch (Exception exception) {
            LOGGER.error("Password reset email delivery failed userId={}", event.userId(), exception);
        }
    }
}

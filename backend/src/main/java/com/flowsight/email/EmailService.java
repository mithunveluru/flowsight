package com.flowsight.email;

public interface EmailService {

    void sendPasswordReset(String toEmail, String recipientName, String resetUrl);
}

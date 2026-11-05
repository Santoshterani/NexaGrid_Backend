package com.telecom.platform.auth;

public interface EmailSender {
    void send(String to, String subject, String body);
}

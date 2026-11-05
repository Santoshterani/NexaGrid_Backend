package com.telecom.platform.auth;

import com.telecom.platform.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Development stand-in. A real SMTP or provider sender replaces this in the notifications phase. */
@Component
public class LoggingEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);
    private final boolean logBody;

    public LoggingEmailSender(AppProperties props) { this.logBody = props.dev().logOtp(); }

    @Override
    public void send(String to, String subject, String body) {
        if (logBody) log.warn("[DEV EMAIL] to={} subject={} body={}", to, subject, body);
        else log.info("Email to {} accepted, but no mail transport is configured yet", to);
    }
}

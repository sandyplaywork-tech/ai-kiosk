package com.aikiosk.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "kiosk")
public class KioskProperties {

    private final Session session = new Session();
    private final Admin admin = new Admin();
    private final Llm llm = new Llm();
    private final Cors cors = new Cors();
    private final Email email = new Email();

    public Session getSession() {
        return session;
    }

    public Admin getAdmin() {
        return admin;
    }

    public Llm getLlm() {
        return llm;
    }

    public Cors getCors() {
        return cors;
    }

    public Email getEmail() {
        return email;
    }

    public static class Session {
        private int lengthMinutes;
        private int tokenCap;
        private int retentionHours;
        private int warningMinutesRemaining;
        private int warningTokenPercent;
        private int logoutGraceMinutes;

        public int getLengthMinutes() {
            return lengthMinutes;
        }

        public void setLengthMinutes(int lengthMinutes) {
            this.lengthMinutes = lengthMinutes;
        }

        public int getTokenCap() {
            return tokenCap;
        }

        public void setTokenCap(int tokenCap) {
            this.tokenCap = tokenCap;
        }

        public int getRetentionHours() {
            return retentionHours;
        }

        public void setRetentionHours(int retentionHours) {
            this.retentionHours = retentionHours;
        }

        public int getWarningMinutesRemaining() {
            return warningMinutesRemaining;
        }

        public void setWarningMinutesRemaining(int warningMinutesRemaining) {
            this.warningMinutesRemaining = warningMinutesRemaining;
        }

        public int getWarningTokenPercent() {
            return warningTokenPercent;
        }

        public void setWarningTokenPercent(int warningTokenPercent) {
            this.warningTokenPercent = warningTokenPercent;
        }

        public int getLogoutGraceMinutes() {
            return logoutGraceMinutes;
        }

        public void setLogoutGraceMinutes(int logoutGraceMinutes) {
            this.logoutGraceMinutes = logoutGraceMinutes;
        }
    }

    public static class Admin {
        private String apiKey;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    public static class Llm {
        private String model;
        private int maxTokens;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }

    public static class Cors {
        private String allowedOrigin;

        public String getAllowedOrigin() {
            return allowedOrigin;
        }

        public void setAllowedOrigin(String allowedOrigin) {
            this.allowedOrigin = allowedOrigin;
        }
    }

    public static class Email {
        private String fromAddress;

        public String getFromAddress() {
            return fromAddress;
        }

        public void setFromAddress(String fromAddress) {
            this.fromAddress = fromAddress;
        }
    }
}

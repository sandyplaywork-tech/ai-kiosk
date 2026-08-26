package com.aikiosk.backend.voucher;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vouchers")
public class Voucher {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(unique = true)
    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "voucher_code", unique = true)
    private String voucherCode;

    @Column(name = "token_cap", nullable = false)
    private int tokenCap;

    @Column(name = "session_length_minutes", nullable = false)
    private int sessionLengthMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VoucherStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    protected Voucher() {
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getVoucherCode() {
        return voucherCode;
    }

    public void setVoucherCode(String voucherCode) {
        this.voucherCode = voucherCode;
    }

    public int getTokenCap() {
        return tokenCap;
    }

    public void setTokenCap(int tokenCap) {
        this.tokenCap = tokenCap;
    }

    public int getSessionLengthMinutes() {
        return sessionLengthMinutes;
    }

    public void setSessionLengthMinutes(int sessionLengthMinutes) {
        this.sessionLengthMinutes = sessionLengthMinutes;
    }

    public VoucherStatus getStatus() {
        return status;
    }

    public void setStatus(VoucherStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(Instant activatedAt) {
        this.activatedAt = activatedAt;
    }
}

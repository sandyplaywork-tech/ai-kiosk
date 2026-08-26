package com.aikiosk.backend.auth.dto;

public record LoginRequest(String username, String password, String voucherCode) {
}

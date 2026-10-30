package com.smartqueue.auth.application;

import com.smartqueue.auth.api.dto.AuthResponseDto;

public record AuthResult(AuthResponseDto authResponse, String refreshTokenPlain) {
}

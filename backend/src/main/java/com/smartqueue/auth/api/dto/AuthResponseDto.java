package com.smartqueue.auth.api.dto;

public class AuthResponseDto {

    private UserDto user;
    private String accessToken;

    public AuthResponseDto() {}

    public AuthResponseDto(UserDto user, String accessToken) {
        this.user = user;
        this.accessToken = accessToken;
    }

    // Getters and Setters

    public UserDto getUser() {
        return user;
    }

    public void setUser(UserDto user) {
        this.user = user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}

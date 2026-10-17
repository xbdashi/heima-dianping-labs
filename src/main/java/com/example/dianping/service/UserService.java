package com.example.dianping.service;

public interface UserService {
    void sendCode(String phone);
    String login(String phone, String code);

    void logout(String token);
}

package com.example.dianping.controller;

import cn.hutool.core.util.RandomUtil;
import com.example.dianping.common.Result;
import com.example.dianping.entity.User;
import com.example.dianping.service.UserService;
import com.example.dianping.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@Validated
public class UserController {
    @Autowired
    private UserService userService;
    @PostMapping("/code")
    public Result<String> getCode(@Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
                              @RequestParam("phone") String phone) {
        userService.sendCode(phone);

        return Result.success("验证码已发送");
    }
    @PostMapping("/login")
    public Result<String> login(@Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确") @RequestParam("phone") String phone,
                                @RequestParam("code") String code) {
        return Result.success(userService.login(phone, code));
    }
    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest httpServletRequest) {
        String token = httpServletRequest.getHeader("authorization");
        userService.logout(token);
        return Result.success("登出成功");
    }
    @GetMapping("/me")
    public Result<User> me() {
        return Result.success(UserHolder.getUser());
    }
}

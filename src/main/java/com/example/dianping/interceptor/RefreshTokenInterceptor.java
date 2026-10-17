package com.example.dianping.interceptor;

import com.example.dianping.constant.RedisConstants;
import com.example.dianping.entity.User;
import com.example.dianping.utils.UserHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取请求头中的 authorization
        String token = request.getHeader("authorization");
        if (token == null || token.isBlank()) {
            return true;   // 没带 token：放行，"有没有登录"交给 LoginInterceptor 判断
        }
        String key = RedisConstants.LOGIN_TOKEN_KEY + token;
        String userJson = stringRedisTemplate.opsForValue().get(key);
        if (userJson == null || userJson.isBlank()) {
            return true;   // token 不存在或已过期：当未登录处理，放行
        }
        User user;
        try {
            user = objectMapper.readValue(userJson, User.class);
        } catch (JsonProcessingException e) {
            stringRedisTemplate.delete(key);   // 坏数据自愈（和店铺缓存的脏 JSON 一个思路）
            return true;
        }
        UserHolder.saveUser(user);
        stringRedisTemplate.expire(key, 30, TimeUnit.MINUTES);  // 续期：每次访问重置 30 分钟
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}

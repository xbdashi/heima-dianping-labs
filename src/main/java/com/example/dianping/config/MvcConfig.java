package com.example.dianping.config;

import com.example.dianping.interceptor.LoginInterceptor;
import com.example.dianping.interceptor.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private RefreshTokenInterceptor refreshTokenInterceptor;
    @Autowired
    private LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(refreshTokenInterceptor)
                .addPathPatterns("/**")
                .order(0);                       // 先验票、放柜
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns(
                        "/api/user/me"
                        ,"/api/voucher-order/**"
                )
                .order(1);                       // 再检查要不要拦
    }
}

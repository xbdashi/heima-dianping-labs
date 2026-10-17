package com.example.dianping.service.impl;


import cn.hutool.core.lang.UUID;
import cn.hutool.core.math.MathUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.dianping.constant.RedisConstants;
import com.example.dianping.entity.User;
import com.example.dianping.exception.BadRequestException;
import com.example.dianping.exception.ManyRequestException;
import com.example.dianping.mapper.UserMapper;
import com.example.dianping.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ObjectMapper objectMapper;
    private final String ttl =  "120";

    @Override
    public void sendCode(String phone) {
        DefaultRedisScript<Long> df = new DefaultRedisScript<>();
        df.setLocation(new ClassPathResource("lua/sendCode.lua"));
        df.setResultType(Long.class);
        String code = RandomUtil.randomNumbers(6);
        log.debug("[模拟短信] 验证码:{}", code);
        Long result = stringRedisTemplate.execute(df,
                List.of(RedisConstants.DP_KEY_LOGIN + phone),
                code, ttl);
        if(result > 60){
            throw new ManyRequestException("请求过于频繁,请"+(result-60)+"秒后再试");
        }

    }

    /**
     * 登录 如果没有就自动注册
     * @param phone
     * @param code
     * @return
     */
    @Override
    public String login(String phone, String code) {
        String codeKey = stringRedisTemplate.opsForValue().get(RedisConstants.DP_KEY_LOGIN + phone);
        if(codeKey == null){
            throw new BadRequestException("验证码错误");
        }
        if(!code.equals(codeKey)){
            throw new BadRequestException("验证码错误");
        }
        // 验证码用后即焚（防重放：同一个码不能被第二次使用）
        stringRedisTemplate.delete(RedisConstants.DP_KEY_LOGIN + phone);
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getPhone, phone);
        User user = userMapper.selectOne(qw);
        if(user == null){
            user = new User();
            user.setNickName(phone);
            user.setPhone(phone);
            userMapper.insert(user);
        }
        // 3. 生成 token，把用户信息存进 Redis
        String token = UUID.randomUUID().toString();
        try {
            String userJson = objectMapper.writeValueAsString(user);
            stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_TOKEN_KEY + token, userJson, 30, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return token;
    }

    @Override
    public void logout(String token) {
        stringRedisTemplate.delete(RedisConstants.LOGIN_TOKEN_KEY + token);
    }
}

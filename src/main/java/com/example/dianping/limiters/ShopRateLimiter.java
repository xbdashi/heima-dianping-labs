package com.example.dianping.limiters;

import com.example.dianping.constant.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class ShopRateLimiter {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private final long limitTtl = 1;
    private  DefaultRedisScript<Long> redisScript;

    public ShopRateLimiter(){
        redisScript = new DefaultRedisScript<>();
    }
    public boolean limitByIp(String ip){
        redisScript.setLocation(new ClassPathResource("lua/shop_rate_limit.lua"));
        redisScript.setResultType(Long.class);
        // 返回0 限流
        Long result;
        try {
            result = stringRedisTemplate.execute(redisScript, List.of(RedisConstants.DP_SHOP_LIMIT + ip));
        }catch (DataAccessException e){
            e.printStackTrace();
            return true; // redis出现异常时放行，由闸门保护
        }
        return Objects.equals(result, 1L);
    }
}

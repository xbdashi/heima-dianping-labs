package com.example.dianping;

import com.example.dianping.limiters.ShopRateLimiter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;

@SpringBootTest
public class TestDemo {
    @Autowired
    public DataSource dataSource;
    @Autowired
    public StringRedisTemplate stringRedisTemplate;
    @Autowired
    public ShopRateLimiter shopRateLimiter;
    @Test
    public void testDataSource(){
        try {
            Assertions.assertNotNull(dataSource);
            Assertions.assertTrue(dataSource.getConnection().isValid(1000));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    @Test
    public void testRedis(){
        stringRedisTemplate.opsForValue().set("test1", "hello");
    }
    @Test
    public void testIncrement(){
        boolean result = shopRateLimiter.limitByIp("1");
        System.out.println(result);
    }
}

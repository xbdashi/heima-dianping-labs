package com.example.dianping;

import com.example.dianping.limiters.ShopRateLimiter;
import com.example.dianping.service.ShopService;
import com.example.dianping.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

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
    @Autowired
    private ShopServiceImpl shopService;
    @Test
    public void testReBulid(){
        shopService.renewLock(1L,"a");
    }

    @Test void scheduleTask() {
        java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(
                () -> System.out.println(
                        "[调度器测试] " + LocalDateTime.now()
                ),
                0,
                3,
                TimeUnit.SECONDS
        );
        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    @Test
    public void testTtl(){
        System.out.println(stringRedisTemplate.getExpire("a"));
    }
    @Autowired
    private RedissonClient redissonClient;

    @Test
    public void testRedissonLock() {
        RLock lock = redissonClient.getLock("test:lock");
        System.out.println("拿锁：" + lock.tryLock());
        System.out.println("当前线程持有：" + lock.isHeldByCurrentThread());
        lock.unlock();
        System.out.println("释放完成");
    }
}

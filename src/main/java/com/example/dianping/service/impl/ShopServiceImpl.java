package com.example.dianping.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.dianping.common.Result;
import com.example.dianping.constant.RedisConstants;
import com.example.dianping.entity.Shop;
import com.example.dianping.entity.redis.RedisData;
import com.example.dianping.exception.ManyRequestException;
import com.example.dianping.exception.ShopNotFoundException;
import com.example.dianping.mapper.ShopMapper;
import com.example.dianping.service.ShopService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tomcat.util.json.JSONParser;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.core.NestedRuntimeException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ShopServiceImpl implements ShopService {

    @Autowired
    private ShopMapper shopMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Value("${my.redis.ttl}")
    private Long redisTtl;
    @Value("${my.redis.cache-null}")
    private Long cacheNull;
    // 定义线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    // 定义定时任务线程池
    private static final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);
    @Override
    public Shop selectById(Long id) {
        // 先查询redis，击中返回，没击中查询数据然后存入缓存
        RedisData<Shop> redisShop = selectShopRedis(id);
        if (redisShop != null) {
            String uuidAsync = UUID.randomUUID().toString();
            // 缓存未过期，直接返回
            if (redisShop.getExpire() != null
                    && LocalDateTime.now().isBefore(redisShop.getExpire())
                    ) {
                return redisShop.getData();
            } else { //缓存存在但已过期
                boolean tryLock = tryLock(id, uuidAsync);
                System.out.println(Thread.currentThread().getName() + " 尝试获取锁:"+tryLock);
                if (tryLock) { // 只有拿到锁的线程可以尝试访问数据库
                    try{
                        long start = System.nanoTime();
                        AtomicBoolean lockValid = new AtomicBoolean(true);
                        CACHE_REBUILD_EXECUTOR.submit(() -> {
                            ScheduledFuture<?> scheduledFuture = null ;
                            try {
                                scheduledFuture = scheduler.scheduleAtFixedRate(() -> {
                                    try{
                                        if (!this.renewLock(id, uuidAsync)) {
                                            lockValid.set(false);
                                        }
                                    }catch (Exception e){
                                        lockValid.set(false);
                                        System.out.println("锁续期失败");
                                        e.printStackTrace();
                                    }
                                }, 0, 3, TimeUnit.SECONDS);
                                Shop shop = shopMapper.selectById(id);
                                if (!lockValid.get()) {
                                    return;
                                }
                                if (shop != null) {
                                    // writeShopIntoRedisWithExpire(shop, 30, ChronoUnit.MINUTES);
                                    boolean success = writeCacheIfLock(
                                            RedisConstants.LOCK_SHOP_KEY + id,
                                            RedisConstants.DP_KEY_SHOP + id,
                                            uuidAsync,
                                            shop
                                    );

                                    if (success) {
                                        System.out.println("缓存写入成功");
                                    } else {
                                        System.out.println("锁已失效，放弃写入缓存");
                                    }
                                } else {
                                    System.out.println(Thread.currentThread().getName()+"查询数据为空,存入null值");
                                    // stringRedisTemplate.opsForValue().set(RedisConstants.DP_KEY_SHOP + id, RedisConstants.CACHE_NULL, cacheNull, TimeUnit.SECONDS);
                                    writeCacheIfLock(
                                            RedisConstants.LOCK_SHOP_KEY + id,
                                            RedisConstants.DP_KEY_SHOP + id,
                                            uuidAsync,
                                            RedisConstants.CACHE_NULL
                                    );
                                }
                            }catch (Exception e){
                                e.printStackTrace();
                                System.out.println(Thread.currentThread().getName() + "缓存重建失败");
                            }finally {
                                long costMs = (System.nanoTime() - start) / 1_000_000;
                                System.out.println("缓存重建耗时：" + costMs + " ms");
                                if (scheduledFuture != null) {
                                    scheduledFuture.cancel(false);
                                }
                                unlock(id, uuidAsync);
                            }
                        });
                    }catch (Exception e){
                        unlock(id, uuidAsync);
                        throw e;
                    }

                }
                // 如果没拿到锁就直接返回
                return redisShop.getData();
            }
        }


        // 缓存不存在，继续原来的处理流程
        // 如果执行到这里就说明没有击中缓存
        String uid = UUID.randomUUID().toString();
        boolean retryFailed = false;
        boolean tryLock = tryLock(id, uid);

        if (!tryLock) {
            // 进入方法了说明获取失败
            try {
                for (int i = 0; i < 10; i++) {
                    // 尝试加载缓存
                    RedisData<Shop> shop = selectShopRedis(id);
                    if (shop != null) return shop.getData();
                    // 尝试拿锁

                    if (tryLock(id, uid)) {
                        retryFailed = false;
                        break;
                    }
                    if (i < 9) {
                        Thread.sleep(50);
                    }
                    retryFailed = true;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }

        }
        if (retryFailed) {
            throw new ManyRequestException("请求过于频繁");
        }
        RedisData<Shop> shop;
        Shop shopData = null;
        try {
            // 再次检查缓存
            shop = selectShopRedis(id);
            if (shop != null) return shop.getData();
            System.out.println(
                    Thread.currentThread().getName()
                            + " 查询 MySQL，店铺 ID：" + id
            );
            // Thread.sleep(300); // 模拟查询耗时
            shopData = shopMapper.selectById(id);
            if (shopData == null) {
                // 把空值写入redis
                try {
                    // stringRedisTemplate.opsForValue().set(RedisConstants.DP_KEY_SHOP + id,
                    //        RedisConstants.CACHE_NULL, cacheNull, TimeUnit.SECONDS);
                    writeCacheIfLock(
                            RedisConstants.LOCK_SHOP_KEY + id,
                            RedisConstants.DP_KEY_SHOP + id,
                            uid,
                            RedisConstants.CACHE_NULL
                    );
                } catch (DataAccessException e) {
                    // TODO redis日志
                    System.out.println("Redis连接失败，写入失败");
                }
                throw new ShopNotFoundException("没有id为%s的店铺".formatted(id));
            }
            // 正常查询值存入redis
            try {
                //writeShopIntoRedisWithExpire(shopData, 30, ChronoUnit.MINUTES);
                writeCacheIfLock(
                        RedisConstants.LOCK_SHOP_KEY + id,
                        RedisConstants.DP_KEY_SHOP + id,
                        uid,
                        shopData
                );
            } catch (DataAccessException e) {
                // TODO redis日志
                System.out.println("Redis连接失败，写入失败");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            unlock(id, uid);
        }
        return shopData;

    }

    /**
     * 将店铺数据写入Redis
     * @param lockKey 拿到当前缓存的锁的key
     * @param redisShopKey redis中存储的店铺数据的key
     * @param uid 当前请求的唯一标识，判断redis中拿到锁的人是否与当前一致
     * @param shop 店铺数据
     * @return
     */
    public boolean writeCacheIfLock(String lockKey,String redisShopKey,String uid,Shop shop){
        RedisData<Shop> redisData = RedisData.build(shop, Duration.ofMinutes(30));
        DefaultRedisScript<Long> drs = new DefaultRedisScript<>();
        drs.setLocation(new ClassPathResource("lua/write_cache_if_lock.lua"));
        drs.setResultType(Long.class);
        try {
            String result = objectMapper.writeValueAsString(redisData);
            Long execute = stringRedisTemplate.execute(drs,
                    List.of(lockKey, redisShopKey),
                    uid,
                    result,
                    "0",
                    cacheNull
            );
            return Long.valueOf(1L).equals(execute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 如果没查到数据使用此方法写入缓存
     * @param lockKey
     * @param redisShopKey
     * @param uid
     * @param data  直接填空值
     * @return 是否设置过期时间 如果1 为设置 0为不设置
     */
    public boolean writeCacheIfLock(String lockKey,String redisShopKey,String uid,String data){
        DefaultRedisScript<Long> drs = new DefaultRedisScript<>();
        drs.setLocation(new ClassPathResource("lua/write_cache_if_lock.lua"));
        drs.setResultType(Long.class);
        try {
            Long execute = stringRedisTemplate.execute(drs,
                    List.of(lockKey, redisShopKey),
                    uid,
                    data,
                    "1",
                    cacheNull
            );
            return Long.valueOf(1L).equals(execute);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
    /**
     * 续期逻辑
     * @param id 店铺id
     * @param uid 唯一标识
     * @return
     */
    private boolean renewLock(Long id, String uid){
        DefaultRedisScript<Long> ds = new DefaultRedisScript<>();
        ds.setLocation(new ClassPathResource("lua/add_expire_time.lua"));
        ds.setResultType(Long.class);
        Long result = stringRedisTemplate.execute(
                ds,
                List.of(RedisConstants.LOCK_SHOP_KEY + id),
                uid,
                10L
        );
        return Long.valueOf(1L).equals(result);
    }
    /**
     * 封装RedisData 对象 加上逻辑过期时间
     *
     * @param shop  data数据
     * @param value 值
     * @param unit  时间单位
     * @return
     */
    private void writeShopIntoRedisWithExpire(Shop shop, long value, ChronoUnit unit) {
        // 1. 构建数据对象
        RedisData<Shop> redisData = RedisData.build(shop, Duration.of(value, unit));
        // 2. 存入redis
        try {

            String data = objectMapper.writeValueAsString(redisData);
            stringRedisTemplate.opsForValue().set(RedisConstants.DP_KEY_SHOP + shop.getId(), data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public RedisData<Shop> selectShopRedis(Long id) {
        String shopJson = null;
        try {
            shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.DP_KEY_SHOP + id);
        } catch (DataAccessException e) {
            // TODO redis日志
            System.out.println("Redis连接失败，读取失败");
        }
        try {
            if (shopJson != null && !shopJson.isBlank()) {
                if (RedisConstants.CACHE_NULL.equals(shopJson)) {
                    throw new ShopNotFoundException("没有id为%s的店铺".formatted(id));
                }
                return objectMapper.readValue(shopJson, new TypeReference<RedisData<Shop>>() {
                });
            }
        } catch (JsonProcessingException e) {
            // TODO 日志
            System.out.println("Redis数据反序列化失败");
            // TODO 删除原来的缓存以免后续没覆盖掉
            try {
                stringRedisTemplate.delete(RedisConstants.DP_KEY_SHOP + id);
            } catch (DataAccessException ee) {
                // TODO redis日志
                System.out.println("Redis连接失败，删除失败");
            }
        }
        return null;
    }

    private boolean tryLock(Long id, String uid) {
        // 1. 拼接锁的 key，使用店铺 id 区分不同店铺
        String key = RedisConstants.LOCK_SHOP_KEY + id;
        // 2. 使用 Redis 的 setIfAbsent 获取锁
        //    锁的值设为 "1"，过期时间设为 10 秒
        // 3. 根据执行结果返回是否获取成功
        return BooleanUtil.isTrue(stringRedisTemplate.opsForValue()
                .setIfAbsent(key, uid, 10, TimeUnit.SECONDS));
    }

    private void unlock(Long id, String uid) {
        // 删除当前店铺对应的锁
        // 比较当前的锁的值是否与当前线程的值一致
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setLocation(new ClassPathResource("lua/unlock.lua"));
        stringRedisTemplate.execute(script,
                List.of(RedisConstants.LOCK_SHOP_KEY + id),
                uid);
    }

    @Override
    public List<Shop> selectAll() {
        return shopMapper.selectList(null);
    }

    @Override
    public Long addShop(Shop shop) {
        int row = shopMapper.insert(shop);
        if (row < 1) {
            throw new RuntimeException("店铺添加失败");
        }

        return shop.getId();
    }

    @Override
    public Shop updateById(Long id, Shop shop) {
        // 1. 查询是否存在当前的店铺
        Shop oldSp = shopMapper.selectById(id);
        if (oldSp == null) {
            throw new ShopNotFoundException("没有id为%s的店铺".formatted(id));
        }
        // 2. 修改ById
        BeanUtils.copyProperties(shop, oldSp);
        shopMapper.updateById(oldSp);

        // 删除redis缓存
        try {
            stringRedisTemplate.delete(RedisConstants.DP_KEY_SHOP + id);
        } catch (DataAccessException e) {
            // TODO redis日志
            System.out.println("Redis连接失败，删除失败");
        }
        return oldSp;
    }

    @Override
    public void deleteById(Long id) {

        var shop = shopMapper.selectById(id);
        if (shop == null) {
            throw new ShopNotFoundException("没有id为%s的店铺".formatted(id));
        }
        shopMapper.deleteById(id);
        try {
            stringRedisTemplate.delete(RedisConstants.DP_KEY_SHOP + id);
        } catch (DataAccessException e) {
            // TODO redis日志
            System.out.println("Redis连接失败，删除失败");
        }
    }

    @Override
    public Page<Shop> page(Long page, Long size, String name, Long typeId, String sortBy, String order) {
        LambdaQueryWrapper<Shop> qw = new LambdaQueryWrapper<>();
        qw.like(name != null && !name.isBlank(), Shop::getName, name);
        qw.eq(typeId != null, Shop::getTypeId, typeId);
        if (sortBy != null && !sortBy.isBlank()) {
            switch (sortBy) {
                case "avgPrice":
                    if ("desc".equals(order)) {
                        qw.orderByDesc(Shop::getAvgPrice);
                    } else if ("asc".equals(order)) {
                        qw.orderByAsc(Shop::getAvgPrice);
                    } else {
                        qw.orderByAsc(Shop::getId);
                    }
                    break;
                case "createTime":
                    if ("desc".equals(order)) {
                        qw.orderByDesc(Shop::getCreateTime);
                    } else if ("asc".equals(order)) {
                        qw.orderByAsc(Shop::getCreateTime);
                    } else {
                        qw.orderByAsc(Shop::getId);
                    }
                    break;
                default:
                    qw.orderByAsc(Shop::getId);
            }
        } else {
            qw.orderByAsc(Shop::getId);
        }
        Page<Shop> shops = new Page<>(page, size);
        return shopMapper.selectPage(shops, qw);
    }
}

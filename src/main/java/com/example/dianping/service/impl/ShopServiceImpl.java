package com.example.dianping.service.impl;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.dianping.constant.RedisConstants;
import com.example.dianping.entity.Shop;
import com.example.dianping.entity.redis.RedisData;
import com.example.dianping.exception.ManyRequestException;
import com.example.dianping.exception.ServiceUnavailableException;
import com.example.dianping.exception.ShopNotFoundException;
import com.example.dianping.mapper.ShopMapper;
import com.example.dianping.service.ShopService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class ShopServiceImpl implements ShopService {

    @Autowired
    private ShopMapper shopMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RedissonClient redissonClient;
    @Value("${my.redis.ttl}")
    private Long redisTtl;
    @Value("${my.redis.cache-null}")
    private Long cacheNull;
    // 定义线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    // 定义定时任务线程池
    private static final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);
    private final Semaphore degradeGate = new Semaphore(8); // 降级限流器
    private static final int REDIS_FAILURE_THRESHOLD = 3; // 连续失败最大次数
    private static final long REDIS_OPEN_MILLIS = 5000; // 熔断时间5秒
    private final AtomicInteger redisFailures = new AtomicInteger(0); // 连续失败次数计数器
    private volatile long redisOpenUntil = 0;
    @Override
    public Shop selectById(Long id) {
        if (!redisAllowAttempt()) {
            log.debug("熔断开路中,跳过 Redis 直接降级, shopId={}", id);
            return degradeSelectById(id);
        }
        // 先查询redis，击中返回，没击中查询数据然后存入缓存
        RedisData<Shop> redisShop=null;
        try {
            redisShop = selectShopRedis(id);
            redisRecordSuccess();
        } catch (DataAccessException e) {
            log.warn("Redis 读取失败,进入降级通道, shopId={}", id, e);
            redisRecordFailure();
            return degradeSelectById(id);
        }
        if (!Objects.isNull(redisShop)) {
            // String uuidAsync = UUID.randomUUID().toString();
            // 缓存未过期，直接返回
            if (redisShop.getExpire() != null
                    && LocalDateTime.now().isBefore(redisShop.getExpire())
            ) {
                return redisShop.getData();
            } else { // 缓存存在但已过期：交给后台"谁抢到锁谁重建"，本请求直接返回旧数据
                CACHE_REBUILD_EXECUTOR.submit(() -> rebuildCache(id));
                return redisShop.getData();
            }

        }
        // ===== 缓存不存在（回源路径）：Redisson 锁 =====
        RLock lock = redissonClient.getLock(RedisConstants.LOCK_SHOP_KEY + id);
        boolean retryFailed = false;
        boolean locked = lock.tryLock();   // 不等待；不传租约时间 → 看门狗自动续期
        if (!locked) {
            try {
                for (int i = 0; i < 10; i++) {
                    RedisData<Shop> shop = selectShopRedis(id);
                    if (shop != null) return shop.getData();
                    if (lock.tryLock()) {
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
        Shop shopData = null;
        try {
            // 拿到锁后二次查缓存（避免重复查库）
            RedisData<Shop> shop = selectShopRedis(id);
            if (shop != null) return shop.getData();
            log.debug("回源查库, shopId={}", id);
            shopData = queryShopFromDb(id);
            if (shopData == null) {
                writeNullCache(lock, id);
                throw new ShopNotFoundException("没有id为%s的店铺".formatted(id));
            }
            writeShopCache(lock, id, shopData);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return shopData;
    }
    // 逻辑过期重建：后台线程自己抢锁执行（Redisson 锁认线程：谁抢谁放）
    // 看门狗负责续期——原来的 renewLock + 调度器 + add_expire_time.lua 全部下岗
    private void rebuildCache(Long id) {
        RLock lock = redissonClient.getLock(RedisConstants.LOCK_SHOP_KEY + id);
        if (!lock.tryLock()) {
            return;   // 没抢到：别的线程正在重建
        }
        long start = System.nanoTime();
        try {
            Shop shop = shopMapper.selectById(id);
            if (shop != null) {
                writeShopCache(lock, id, shop);
            } else {
                writeNullCache(lock, id);
            }
        } catch (Exception e) {
            log.error("缓存重建失败, shopId={}", id, e);

        } finally {
            log.info("缓存重建流程结束, shopId={}, 耗时={}ms", id, (System.nanoTime() - start) / 1_000_000);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // 持有锁期间写回店铺缓存（看门狗保证持有期间锁不过期；写前用本地状态兜底检查）
    private void writeShopCache(RLock lock, Long id, Shop shop) {
        if (!lock.isHeldByCurrentThread()) {
            log.debug("锁已失效,放弃写入缓存, shopId={}", id);
            return;
        }
        try {
            RedisData<Shop> redisData = RedisData.build(shop, Duration.ofMinutes(30 + RandomUtil.randomInt(5)));
            String json = objectMapper.writeValueAsString(redisData);
            stringRedisTemplate.opsForValue().set(RedisConstants.DP_KEY_SHOP + id, json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (DataAccessException e) {
            log.warn("店铺缓存写入失败, shopId={}", id, e);
        }
    }

    // 负缓存写入（短 TTL，物理过期）
    private void writeNullCache(RLock lock, Long id) {
        if (!lock.isHeldByCurrentThread()) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    RedisConstants.DP_KEY_SHOP + id,
                    RedisConstants.CACHE_NULL,
                    cacheNull,
                    TimeUnit.SECONDS
            );
        } catch (DataAccessException e) {
            log.warn("空值缓存写入失败, shopId={}", id, e);
        }
    }
    // 受闸门保护的查库：前台所有查 MySQL 都从这里走
    private Shop queryShopFromDb(Long id) {
        if (!degradeGate.tryAcquire()) {
            throw new ServiceUnavailableException("服务繁忙，请稍后再试");
        }
        try {
            return shopMapper.selectById(id);
        } finally {
            degradeGate.release();
        }
    }
    private Shop degradeSelectById(Long id) {
        Shop shop = queryShopFromDb(id);
        if (shop == null) {
            throw new ShopNotFoundException("没有id为%s的店铺".formatted(id));
        }
        return shop;
    }
    /**
     * System.currentTimeMillis() 是当前时间的毫秒数（一个大数字）。这一行就是拿"现在"和"可以再试的时间点"比：
     * - 闭合时 redisOpenUntil = 0：现在永远 ≥ 0，返回 true，可以试；
     * - 开路中：redisOpenUntil 是未来时间，现在 < 它，返回 false，跳过；
     * - 冷却到点：现在 ≥ 它，返回 true，允许去试（这就是"探测"）。
     * @return
     */
    private boolean redisAllowAttempt() {
        return System.currentTimeMillis() >= redisOpenUntil;
    }
    private void redisRecordSuccess() {
        if (redisOpenUntil != 0) {
            log.info("Redis 熔断恢复,已闭合");
        }
        redisFailures.set(0);
        redisOpenUntil = 0;
    }
    private void redisRecordFailure() {
        int failures = redisFailures.incrementAndGet();
        log.debug("Redis 连续失败次数:{}", failures);
        if (failures >= REDIS_FAILURE_THRESHOLD) {
            redisOpenUntil = System.currentTimeMillis() + REDIS_OPEN_MILLIS;
            log.warn("Redis 连续失败 {} 次,熔断开启 {} 秒", failures, REDIS_OPEN_MILLIS / 1000);
        }
    }
    /**
     * 续期逻辑
     *
     * @param id  店铺id
     * @param uid 唯一标识
     * @return
     */
    public boolean renewLock(Long id, String uid) {
        DefaultRedisScript<Long> ds = new DefaultRedisScript<>();
        ds.setLocation(new ClassPathResource("lua/add_expire_time.lua"));
        ds.setResultType(Long.class);
        Long result = stringRedisTemplate.execute(
                ds,
                List.of(RedisConstants.LOCK_SHOP_KEY + id),
                uid,
                "10"
        );
        return Long.valueOf(1).equals(result);
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
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.DP_KEY_SHOP + id);
        try {
            if (shopJson != null && !shopJson.isBlank()) {
                if (RedisConstants.CACHE_NULL.equals(shopJson)) {
                    throw new ShopNotFoundException("没有id为%s的店铺".formatted(id));
                }
                return objectMapper.readValue(shopJson, new TypeReference<RedisData<Shop>>() {
                });
            }
        } catch (JsonProcessingException e) {
            log.warn("缓存反序列化失败,准备清除脏数据, shopId={}", id, e);
            try {
                stringRedisTemplate.delete(RedisConstants.DP_KEY_SHOP + id);
            } catch (DataAccessException ee) {
                log.warn("脏缓存清除失败, shopId={}", id, ee);
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
            log.error("更新后缓存删除失败,可能读到旧数据, shopId={}", id, e);
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
            log.error("删除后缓存清理失败,已删店铺仍可能被读到, shopId={}", id, e);
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

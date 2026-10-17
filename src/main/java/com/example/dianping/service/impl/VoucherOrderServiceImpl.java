package com.example.dianping.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.dianping.constant.RedisConstants;
import com.example.dianping.entity.SeckillVoucher;
import com.example.dianping.entity.VoucherOrder;
import com.example.dianping.exception.BadRequestException;
import com.example.dianping.exception.ManyRequestException;
import com.example.dianping.mapper.SeckillVoucherMapper;
import com.example.dianping.mapper.VoucherOrderMapper;
import com.example.dianping.service.VoucherOrderService;
import com.example.dianping.utils.RedisIdWorker;
import com.example.dianping.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class VoucherOrderServiceImpl implements VoucherOrderService {

    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private VoucherOrderMapper voucherOrderMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final DefaultRedisScript<Long> df;

    static {
        df = new DefaultRedisScript<>();
        df.setResultType(Long.class);
        df.setLocation(new ClassPathResource("lua/seckill.lua"));
    }

    @Override
    public Long seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate
                .execute(df,
                        List.of(
                                RedisConstants.SECKILL_STOCK_KEY + voucherId
                                , RedisConstants.SECKILL_ORDER_KEY + voucherId
                        ),
                        String.valueOf(userId)
                );
        int re = result == null ? 1 : result.intValue();
        if (re == 1) {
            throw new BadRequestException("库存不足");
        }
        if (re == 2) {
            throw new BadRequestException("你已经购买过该优惠券");
        }
        // 一人一单
        LambdaQueryWrapper<VoucherOrder> wq = new LambdaQueryWrapper<>();
        wq.eq(VoucherOrder::getUserId, UserHolder.getUser().getId());
        wq.eq(VoucherOrder::getVoucherId, voucherId);
        Long count = voucherOrderMapper.selectCount(wq);
        if (count != null && count > 0) {
            restoreStock(voucherId); // 回滚库存
            throw new BadRequestException("你已经拥有该优惠券");
        }
        // 2. 扣减库存
        int row = seckillVoucherMapper.deductStock(voucherId);
        if (row == 0) {
            stringRedisTemplate.opsForSet().remove(
                    RedisConstants.SECKILL_ORDER_KEY + voucherId, userId.toString());
            stringRedisTemplate.opsForValue().set(RedisConstants.SECKILL_STOCK_KEY + voucherId, "0");
            log.error("秒杀库存漂移:DB 已无库存但 Redis 仍有余量,voucherId={}", voucherId);
            throw new BadRequestException("库存不足");
        }
        // 3. 创建id
        long orderId = redisIdWorker.nextId("order");
        // 4. 写入订单表
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(UserHolder.getUser().getId());
        order.setVoucherId(voucherId);
        order.setPayType(1);
        order.setStatus(1);
        try {
            voucherOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            log.error("用户已下过单");
            restoreStock(voucherId); // 回滚库存即可
            throw new BadRequestException("你已经下过单");
        }
        return orderId;

    }

    private void restoreStock(Long voucherId) {
        stringRedisTemplate.opsForValue().increment(RedisConstants.SECKILL_STOCK_KEY + voucherId);
    }
}

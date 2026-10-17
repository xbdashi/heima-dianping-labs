package com.example.dianping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.dianping.entity.SeckillVoucher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {

    /**
     * 扣减库存(返回受影响行数)
     * v1:普通版。压测暴露问题后,就改这一条 SQL。
     */
    @Update("UPDATE seckill_voucher SET stock = stock - 1 WHERE voucher_id = #{voucherId} AND stock > 0")
    int deductStock(@Param("voucherId") Long voucherId);
}

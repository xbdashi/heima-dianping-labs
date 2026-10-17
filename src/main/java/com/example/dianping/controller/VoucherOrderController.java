package com.example.dianping.controller;

import com.example.dianping.common.Result;
import com.example.dianping.service.VoucherOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/voucher-order")
public class VoucherOrderController {

    @Autowired
    private VoucherOrderService voucherOrderService;

    @PostMapping("/seckill/{voucherId}")
    public Result<Long> seckillVoucher(@PathVariable("voucherId") Long voucherId) {
        return Result.success(voucherOrderService.seckillVoucher(voucherId));
    }
}

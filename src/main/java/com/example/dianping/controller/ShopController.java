package com.example.dianping.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.dianping.common.Result;
import com.example.dianping.entity.Shop;
import com.example.dianping.exception.ManyRequestException;
import com.example.dianping.limiters.ShopRateLimiter;
import com.example.dianping.service.ShopService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shops")
@Validated
public class ShopController {

    @Autowired
    private ShopService shopService;
    @Autowired
    private ShopRateLimiter shopRateLimiter;
    @GetMapping("/{id}")
    public Result<Shop> getShopById(@PathVariable("id") @Positive(message = "请填写正确id") Long id, HttpServletRequest httpServlet){
        if (!shopRateLimiter.limitByIp(httpServlet.getRemoteAddr())) {
            throw new ManyRequestException("请求过于频繁");
        }
        return Result.success(shopService.selectById(id));
    }
    @GetMapping
    public List<Shop> getShopAll(){
        return shopService.selectAll();
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<Long> addShop(@Valid  @RequestBody Shop shop){
        return Result.success(shopService.addShop(shop));
    }
    @PutMapping("/{id}")
    public Result<Shop> updateById(@PathVariable("id") Long id, @RequestBody Shop shop){
        return Result.success(shopService.updateById(id,shop));
    }
    @DeleteMapping("/{id}")
    public Result<Void> deleteById(@PathVariable("id") Long id){
        shopService.deleteById(id);
        return Result.success();
    }
    @GetMapping("/pages")
    public Result<Page<Shop>> page(@RequestParam(value = "page",defaultValue = "1") @Max(100) @Positive(message = "至少要大于0") Long page
            ,@RequestParam(value = "size",defaultValue = "10") @Min(value = 1,message ="最小为5页" ) @Max(20) Long size
            ,@RequestParam(value = "name",required = false) String shopName
            ,@RequestParam(value = "sortBy",required = false) String sortBy
            ,@RequestParam(value = "order",required = false) String order
            ,@RequestParam(value = "typeId",required = false) @Positive(message = "类型 ID 必须大于 0") Long typeId){
        return Result.success(shopService.page(page,size,shopName,typeId,sortBy,order));
    }

}

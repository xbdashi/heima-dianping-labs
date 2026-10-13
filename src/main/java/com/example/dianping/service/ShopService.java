package com.example.dianping.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.dianping.common.Result;
import com.example.dianping.entity.Shop;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

public interface ShopService {
    Shop selectById(Long id);
    List<Shop> selectAll();

    Long addShop(Shop shop);

    Shop updateById(Long id, Shop shop);

    void deleteById(Long id);

    Page<Shop> page(Long page, Long size, String name, Long typeId, String sortBy, String order);

}

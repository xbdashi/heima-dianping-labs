package com.example.dianping.constant;

public class RedisConstants {

    public static final String DP_KEY_SHOP="dp:shop:";
    public static final String DP_KEY_LOGIN="dp:login:code:";
    public static final String CACHE_NULL ="NOT_EXIST" ;
    public static final String DP_SHOP_LIMIT ="rate_limit:shop:";
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final String LOGIN_TOKEN_KEY = "dp:login:token:";
    public static final String LOCK_SECKILL_KEY = "lock:seckill:";
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";   // 秒杀库存
    public static final String SECKILL_ORDER_KEY = "seckill:order:";   // 已下单用户集合(Set)
}

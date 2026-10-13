package com.example.dianping.entity.redis;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * 逻辑过期数据缓存类
 */
public class RedisData<T> {
    private T data;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expire;


    public static <T> RedisData<T> build(T data, Duration duration) {
        RedisData<T> redisData = new RedisData<>();
        redisData.setData(data);
        redisData.setExpire(LocalDateTime.now().plus(duration));
        return redisData;
    }
}

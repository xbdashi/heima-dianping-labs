-- KEYS[1] = 秒杀库存 key(seckill:stock:{voucherId})
-- KEYS[2] = 已购用户集合 key(seckill:order:{voucherId})
-- ARGV[1] = 用户 id
local stockKey = KEYS[1]
local orderKey = KEYS[2]
local userId = ARGV[1]

-- 1. 库存校验(未预热/已售罄都返回 1)
local stock = tonumber(redis.call('get', stockKey))
if stock == nil or stock <= 0 then
    return 1
end

-- 2. 一人一单校验
-- 判断用户是否已购买 当前订单key 然后找对应的用户id 如果找到返回1
if redis.call('sismember', orderKey, userId) == 1 then
    return 2
end

-- 3. 扣库存 + 记录用户
redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
return 0

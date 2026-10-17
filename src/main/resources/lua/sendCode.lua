local key = KEYS[1]
local code = ARGV[1]
local ttl = ARGV[2]
local expire = redis.call("ttl",key)

if expire > 60 then
    return expire
end
redis.call("set",key,code)
redis.call("expire",key,ttl)
return 0

local count = redis.call("incr", KEYS[1])

if count == 1 then
    redis.call("expire", KEYS[1],1)
end

if count > 10 then
    return 0
end

return 1



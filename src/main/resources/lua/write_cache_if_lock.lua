local lockValue = redis.call("get", KEYS[1])

if lockValue == ARGV[1] then
    redis.call("set", KEYS[2], ARGV[2])

    if ARGV[3] == "1" then
        redis.call("expire", KEYS[2], tonumber(ARGV[4]))
    end

    return 1
end

return 0

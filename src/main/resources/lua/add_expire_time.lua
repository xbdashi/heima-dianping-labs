local key = KEYS[1]
local uid = ARGV[1]
local seconds = tonumber(ARGV[2])

local s = redis.call("get", key)

if s == uid then
    return redis.call("expire", key, seconds)
end

return 0

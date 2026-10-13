local key = KEYS[1]
local uid = ARGV[1]

local s = redis.call("get", key)
if s==uid then
    redis.call("del", key)
    return 1
end

return 0

if redis.call('GET', KEYS[1]) == ARGV[1] then
    local ttl = redis.call('PTTL', KEYS[1])
    if ttl > 0 then
        redis.call('SET', KEYS[1], ARGV[2], 'PX', ttl)
        return 1
    end
end
return 0

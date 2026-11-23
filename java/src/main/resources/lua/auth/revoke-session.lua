local value = redis.call('GET', KEYS[1])
if value and string.sub(value, 1, string.len(ARGV[1]) + 1) == ARGV[1] .. ':' then
    return redis.call('DEL', KEYS[1])
end
return 0

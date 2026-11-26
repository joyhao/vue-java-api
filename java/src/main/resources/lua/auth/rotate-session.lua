-- 轮换会话里的 refresh token（比较并交换）。
-- ARGV[1] = 旧值（sid:sha256(旧refresh)），ARGV[2] = 新值，ARGV[3] = sid
-- 返回 1 = 已轮换（保留剩余有效期）；-1 = 旧凭证被重放，会话已删除；0 = 会话不存在或凭证不匹配
local value = redis.call('GET', KEYS[1])
if not value then return 0 end
if value == ARGV[1] then
    local ttl = redis.call('PTTL', KEYS[1])
    if ttl > 0 then
        redis.call('SET', KEYS[1], ARGV[2], 'PX', ttl)
        return 1
    end
    return 0
end
-- 同一个会话、但不是当前这一代凭证：说明一个已经用过的 refresh token 又被提交了一次，
-- 视为凭证泄露，把整个会话吊销，让持有者和真正用户都必须重新登录。
if string.sub(value, 1, string.len(ARGV[3]) + 1) == ARGV[3] .. ':' then
    redis.call('DEL', KEYS[1])
    return -1
end
return 0

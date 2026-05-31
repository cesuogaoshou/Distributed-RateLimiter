local bucket_key = KEYS[1]
local tokens_key = bucket_key .. ':tokens'
local timestamp_key = bucket_key .. ':timestamp'

local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local permits = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

local current_tokens = tonumber(redis.call('GET', tokens_key))
if current_tokens == nil then
    current_tokens = capacity
end

local last_refill = tonumber(redis.call('GET', timestamp_key))
if last_refill == nil then
    last_refill = now
end

local elapsed = math.max(0, now - last_refill) / 1000
local refill = elapsed * refill_rate
current_tokens = math.min(capacity, current_tokens + refill)

local allowed = 0
if current_tokens >= permits then
    current_tokens = current_tokens - permits
    allowed = 1
end

redis.call('SET', tokens_key, current_tokens)
redis.call('SET', timestamp_key, now)
redis.call('PEXPIRE', tokens_key, 60000)
redis.call('PEXPIRE', timestamp_key, 60000)

return { allowed, math.floor(current_tokens) }

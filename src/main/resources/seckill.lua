---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by think.
--- DateTime: 2024/11/21 16:15
--- ARGV列表：优惠券id，用户id，订单id
--- 返回值：0表示创建订单，1表示库存不足，2表示一人多次下单

--- 1.判断库存是否充足
local stockKey = 'seckill:stock:' .. ARGV[1]
if(tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end

--- 2.判断一人一单
local orderKey = 'seckill:order:' .. ARGV[1]
if(redis.call('sismember', orderKey, ARGV[2]) == 1) then  --- 存在返回1，不存在返回0
    return 2
end

--- 3.扣库存
redis.call('incrby', stockKey, -1)
--- 4.保存订单
redis.call('sadd', orderKey, ARGV[2])
--- 5.发送消息到队列中 XADD 队列名称 * k1 v1 k2 v2 ...
--- redis.call('xadd', 'stream.orders', '*', 'userId', ARGV[2], 'voucherId', ARGV[1], 'id', ARGV[3])

return 0

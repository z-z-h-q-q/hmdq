package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.Synchronized;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始，是否结束
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始！");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }
        // 3.判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足！");
        }
        Long userId = UserHolder.getUser().getId();
//        synchronized(userId.toString().intern()){
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy(); // 获得当前对象的代理对象
//            return proxy.createVoucherOrder(voucherId);
//        }
        // 创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁，时间与业务执行有关
        //boolean isLock = lock.tryLock(1200);
        boolean isLock = lock.tryLock(); // 默认等待时间为-1，即不等待；过期时间为30s
        if (!isLock) {
            // 返回错误或重试
            return Result.fail("不允许重复下单");
        }
        try{
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy(); // 获得当前对象的代理对象
            return proxy.createVoucherOrder(voucherId);
        }finally {
            lock.unlock();
        }

    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 4.一人一单（悲观锁）
        // 4.1查询订单
        Long userId = UserHolder.getUser().getId();
        long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 4.2判断
        if (count > 0) {
            return Result.fail("用户已经购买过一次！");
        }

        // 5.扣减库存（乐观锁，用于更新情况）
        boolean result = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if(!result){
            return Result.fail("库存不足！");
        }

        // 6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        save(voucherOrder);
        // 7.返回
        return Result.ok(orderId);
    }
}

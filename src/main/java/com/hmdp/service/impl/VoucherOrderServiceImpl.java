package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constant.MqConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.interceptor.RateLimit;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RabbitTemplate rabbitTemplate;

    private final Map<String, Boolean> voucherStorage = new HashMap<>();

    @PostConstruct
    private void init(){
        List<Long> ids = seckillVoucherService.lambdaQuery().select(SeckillVoucher::getVoucherId).list()
                .stream().map(SeckillVoucher::getVoucherId).collect(Collectors.toList());
        for(Long id : ids){
            voucherStorage.put(String.valueOf(id),true);
        }
    }

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
    }

    @RateLimit(10.0)
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 0.使用内存判断
        if(!voucherStorage.get(String.valueOf(voucherId))){
            return Result.fail("内存不足");
        }
        // 1.执行lua脚本
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());
        // 2.判断结果是否为0
        int r = result.intValue();
        if(r != 0){
            if(r == 1){
                voucherStorage.put(String.valueOf(voucherId),false);
            }
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 3.异步下单
        VoucherOrder order = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        rabbitTemplate.convertAndSend(MqConstants.EXCHANGE_NAME, MqConstants.KEY, JSONUtil.toJsonStr(order));
        // 返回订单id
        return Result.ok("成功加入下单队列，正在排队中...");
    }

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        // 1.查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2.判断秒杀是否开始，是否结束
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始！");
//        }
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束！");
//        }
//        // 3.判断库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足！");
//        }
//        Long userId = UserHolder.getUser().getId();
////        synchronized(userId.toString().intern()){
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy(); // 获得当前对象的代理对象
////            return proxy.createVoucherOrder(voucherId);
////        }
//        // 创建锁对象
//        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        // 获取锁，时间与业务执行有关
//        //boolean isLock = lock.tryLock(1200);
//        boolean isLock = lock.tryLock(); // 默认等待时间为-1，即不等待；过期时间为30s
//        if (!isLock) {
//            // 返回错误或重试
//            return Result.fail("不允许重复下单");
//        }
//        try{
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy(); // 获得当前对象的代理对象
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            lock.unlock();
//        }
//
//    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 4.一人一单（悲观锁）
        // 4.1查询订单
        Long userId = voucherOrder.getUserId();
        long count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        // 4.2判断
        if (count > 0) {
            log.error("用户已经购买过一次！");
            return;
        }

        // 5.扣减库存（乐观锁，用于更新情况）
        boolean result = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                .update();
        if(!result){
            log.error("库存不足！");
            return;
        }

        // 6.创建订单
        save(voucherOrder);
    }
}

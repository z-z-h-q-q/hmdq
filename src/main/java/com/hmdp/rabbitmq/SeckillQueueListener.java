package com.hmdp.rabbitmq;

import cn.hutool.json.JSONUtil;
import com.hmdp.constant.MqConstants;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Slf4j
@Component
@RabbitListener(queues = MqConstants.QUEUE_NAME)
public class SeckillQueueListener {
    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Transactional

    @RabbitHandler
    public void receiveSeckillMessage(String msg){
        log.info("接收到消息: "+msg);
        VoucherOrder order = JSONUtil.toBean(msg, VoucherOrder.class);
        //5.一人一单
        Long voucherId = order.getVoucherId();
        Long userId = order.getUserId();
        //5.1查询订单
        long count = voucherOrderService.query().eq("user_id",userId).eq("voucher_id", voucherId).count();
        //5.2判断是否存在
        if(count > 0){
            //用户已经购买过了
            log.error("该用户已购买过");
            return;
        }
        log.info("扣减库存");
        //6.扣减库存
        boolean success = seckillVoucherService
                .update()
                .setSql("stock = stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)//cas乐观锁
                .update();
        if(!success){
            log.error("库存不足");
            return;
        }
        //直接保存订单
        voucherOrderService.save(order);
    }
}

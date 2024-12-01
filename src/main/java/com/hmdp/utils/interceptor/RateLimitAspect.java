package com.hmdp.utils.interceptor;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Aspect
@Slf4j
@Component
public class RateLimitAspect {
    // 使用 ConcurrentHashMap 存储每个方法的 RateLimiter
    private final Map<String, RateLimiter> rateLimiterMap = new ConcurrentHashMap<>();

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // 1.获取方法的唯一标识符
        String methodKey = joinPoint.getSignature().toShortString();
        // 2.若不存在，初始化 RateLimiter
        rateLimiterMap.computeIfAbsent(methodKey, key -> {
            log.info("为方法 {} 初始化 RateLimiter，每秒允许 {} 个请求", key, rateLimit.value());
            return RateLimiter.create(rateLimit.value());
        });
        // 3.获取对应的 RateLimiter
        RateLimiter rateLimiter = rateLimiterMap.get(methodKey);
        // 4.尝试获取令牌
        if (rateLimiter.tryAcquire()) {
            // 成功获取令牌，执行方法
            return joinPoint.proceed();
        } else {
            // 限流逻辑：可以抛出异常或返回自定义消息
            log.warn("方法 {} 被限流", methodKey);
            throw new RuntimeException("请求过于频繁，请稍后再试");
        }
    }
}

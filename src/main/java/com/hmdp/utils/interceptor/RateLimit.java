package com.hmdp.utils.interceptor;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    // 每秒允许的请求数
    double value() default 5.0;
}

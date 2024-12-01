package com.hmdp.config;

import cn.hutool.extra.mail.MailAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MailConfig {
    @Value("${spring.mail.host}")
    private String host;
    @Value("${spring.mail.port}")
    private String port;
    @Value("${spring.mail.username}")
    private String username;
    @Value("${spring.mail.password}")
    private String password;

    @Bean
    public MailAccount mailAccount() {
        MailAccount account = new MailAccount();
        account.setHost(host); // SMTP 服务地址
        account.setPort(Integer.parseInt(port)); // SMTP 端口
        account.setFrom(username + "<" + username + ">"); // 设置发送人邮箱(用户名 <邮箱地址>)
        account.setUser(username); // 用户名
        account.setPass(password); // 授权码
        account.setAuth(true); // 启用SMTP认证
        account.setStarttlsEnable(true); // 启用 TLS
        return account;
    }
}

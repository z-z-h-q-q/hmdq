package com.hmdp.utils;

import cn.hutool.core.lang.Dict;
import cn.hutool.extra.mail.Mail;
import cn.hutool.extra.mail.MailAccount;
import cn.hutool.extra.template.Template;
import cn.hutool.extra.template.TemplateConfig;
import cn.hutool.extra.template.TemplateEngine;
import cn.hutool.extra.template.TemplateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class EmailUtil {
    @Resource
    private MailAccount mailAccount;

    public void sendVerificationEmail(String toEmail, String code){
        try {
            // 渲染 FreeMarker 模板
            TemplateEngine engine = TemplateUtil.createEngine(new TemplateConfig("templates", TemplateConfig.ResourceMode.CLASSPATH));
            Template template = engine.getTemplate("email-code.ftl");
            String emailContent = template.render(Dict.create().set("code", code));

            // 发送邮件
            Mail.create(mailAccount)
                    .setTos(toEmail) // 收件人
                    .setTitle("验证码邮件") // 邮件标题
                    .setContent(emailContent) // 邮件内容
                    .setHtml(true) // 是否为 HTML 格式
                    // 关闭session
                    .setUseGlobalSession(false)
                    .send();

            log.info("验证码邮件已发送至：{}", toEmail);
        } catch (Exception e) {
            log.error("邮件发送失败：{}", e.getMessage());
        }
    }
}

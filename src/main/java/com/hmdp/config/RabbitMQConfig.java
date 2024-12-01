package com.hmdp.config;

import com.hmdp.constant.MqConstants;
import com.rabbitmq.client.AMQP;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    @Bean
    public TopicExchange topicExchange() {
        return new TopicExchange(MqConstants.EXCHANGE_NAME);
    }

    @Bean
    public Queue queue(){
        return new Queue(MqConstants.QUEUE_NAME);
    }

    public Binding binding(){
        return BindingBuilder
                .bind(queue())
                .to(topicExchange())
                .with(MqConstants.KEY);
    }
}

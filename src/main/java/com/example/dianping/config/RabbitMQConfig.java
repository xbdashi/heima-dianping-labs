package com.example.dianping.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class RabbitMQConfig {
    public static final String SECKILL_EXCHANGE="seckill_exchange";
    public static final String SECKILL_QUEUE="seckill_order_queue";
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order";

    @Bean
    public DirectExchange seckillExchange(){
        return new DirectExchange(SECKILL_EXCHANGE,true,false);
    }
    @Bean
    public Queue seckillQueue(){
        return QueueBuilder.durable(SECKILL_QUEUE).build();
    }

    /**
     * 依赖注入时，spring会默认去找配置了Bean的Queue ，如果只有一个那就和方法名已经Bean（“名称”）无关
     * 当有多个Bean时，如果Bean上没有写名字，会根据方法名作为Bean的名称来注入
     * 当只有一个队列时，以方法名称为准，在Bean上写名称也无效
     * @param seckillQueue
     * @param seckillExchange
     * @return
     */
    /*@Bean
    public Queue seckillQueue2(){
        return QueueBuilder.durable(SECKILL_QUEUE).build();
    }*/
    @Bean
    public Binding seckillBinding(Queue seckillQueue, DirectExchange seckillExchange){
        return BindingBuilder.bind(seckillQueue)
                .to(seckillExchange)
                .with(SECKILL_ORDER_ROUTING_KEY);
    }
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper){
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}

package com.example.dianping;

import com.example.dianping.config.RabbitMQConfig;
import com.example.dianping.entity.VoucherOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class RabbitMqTest {
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
    public void onMessage(Person order) {
        System.out.println("监听到消息："+order);
    }

    @Test
    public void sendMessage() {
        Person person = new Person("张三", 18);
        rabbitTemplate.convertAndSend(RabbitMQConfig.SECKILL_EXCHANGE
        ,RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,person);
    }

}
@Data
@AllArgsConstructor
class  Person{
    private String name;
    private int age;
}

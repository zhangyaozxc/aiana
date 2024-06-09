package com.yupi.springbootinit.bizmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BIInitMain implements CommandLineRunner {
//    public static void main(String[] args) {
//
//        try {
//            ConnectionFactory factory = new ConnectionFactory();
//            factory.setHost("localhost");
//            Connection connection = factory.newConnection();
//            Channel channel = connection.createChannel();
//
//            String EXCHANGE_NAME = BiMqConstant.BI_EXCHANGE_NAME;
//            channel.exchangeDeclare(EXCHANGE_NAME, "direct");
//
//            String queueName = BiMqConstant.BI_QUEUE_NAME;
//            channel.queueDeclare(queueName, true, false, false, null);
//            channel.queueBind(queueName, EXCHANGE_NAME, BiMqConstant.BI_ROUTING_KEY);
//
//        }catch (Exception e){
//
//        }
//    }

    @Override
    public void run(String... args){
        try {
            log.info("Start RabbitMQ");
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            String EXCHANGE_NAME = BiMqConstant.BI_EXCHANGE_NAME;
            channel.exchangeDeclare(EXCHANGE_NAME, "direct");

            String queueName = BiMqConstant.BI_QUEUE_NAME;
            channel.queueDeclare(queueName, true, false, false, null);
            channel.queueBind(queueName, EXCHANGE_NAME, BiMqConstant.BI_ROUTING_KEY);
        }catch (Exception e){

        }
    }
}

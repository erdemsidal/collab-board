package com.collabboard.config;

import com.collabboard.realtime.BroadcastService;
import com.collabboard.realtime.RedisBroadcastSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis Pub/Sub aboneliği (ADR 0004).
 *
 * RedisMessageListenerContainer: Redis'e ayrı bir bağlantı açıp belirtilen kanalı
 * sürekli dinler; mesaj geldikçe dinleyicimizi (subscriber) çağırır.
 * Bu bean sayesinde her sunucu kopyası "anons hattını" duyar hâle gelir.
 */
@Configuration
public class RedisPubSubConfig {

    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisBroadcastSubscriber subscriber) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(BroadcastService.CHANNEL));
        return container;
    }
}

package com.example.chatserver;

import org.kurento.client.KurentoClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public RoomManager roomManager(KurentoClient kurentoClient) {
        return new RoomManager(kurentoClient);
    }

    @Bean
    public KurentoClient kurentoClient() {
        // Kurento Medya Sunucusu'na bağlanmak için URL'i belirtin
        return KurentoClient.create("ws://localhost:8888/kurento");
    }
}
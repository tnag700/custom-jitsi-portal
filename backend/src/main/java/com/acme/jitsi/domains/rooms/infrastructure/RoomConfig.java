package com.acme.jitsi.domains.rooms.infrastructure;

import com.acme.jitsi.domains.rooms.service.RoomRepository;
import com.acme.jitsi.domains.rooms.service.RoomService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RoomConfig {

  @Bean
  RoomService roomService(
      RoomRepository roomRepository) {
    return new RoomService(roomRepository);
  }
}

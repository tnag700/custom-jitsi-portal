package com.acme.jitsi.support;

import com.acme.jitsi.domains.profiles.service.UserProfileService;
import com.acme.jitsi.domains.rooms.service.ConfigSetValidator;
import com.acme.jitsi.domains.rooms.service.RoomService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

public abstract class MeetingsModuleScaffoldingMocksSupport {

  @MockitoBean
  protected UserProfileService userProfileService;

  @MockitoBean
  protected RoomService roomService;

  @MockitoBean
  protected ConfigSetValidator configSetValidator;
}
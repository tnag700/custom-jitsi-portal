package com.acme.jitsi.infrastructure.idempotency;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("test")
@RestController
@RequestMapping(value = "/api/v1/test")
public class IdempotencyTestController {

  @Idempotent
  @PostMapping("/idempotent")
  ResponseEntity<String> testEndpoint() throws InterruptedException {
    Thread.sleep(100);
    return ResponseEntity.ok("Success");
  }
}
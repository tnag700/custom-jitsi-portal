package com.acme.jitsi.infrastructure.usecase;

public interface UseCase<I, O> {
  O execute(I input);
}

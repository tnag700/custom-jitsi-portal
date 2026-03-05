package com.acme.jitsi.domains.store;

public interface StoreSelectionStrategy<T> {

  T select(String rawMode);
}
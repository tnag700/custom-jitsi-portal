package com.acme.jitsi.shared.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PageSizeTest {
  @Test
  void usesDefaultForNonPositiveValuesAndAcceptsMaximum() {
    assertEquals(20, PageSize.resolve(0));
    assertEquals(20, PageSize.resolve(-1));
    assertEquals(20, PageSize.resolve(20));
    assertEquals(100, PageSize.resolve(100));
  }

  @Test
  void rejectsOversizedPages() {
    assertEquals("size must be <= 100",
        assertThrows(PageSize.InvalidPageSizeException.class, () -> PageSize.resolve(101)).getMessage());
  }
}

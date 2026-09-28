package com.acme.jitsi.shared.validation;

public final class PageSize {
  private PageSize() {}

  public static int resolve(int requested) {
    if (requested > 100) {
      throw new InvalidPageSizeException();
    }
    return requested <= 0 ? 20 : requested;
  }

  public static final class InvalidPageSizeException extends RuntimeException {
    public InvalidPageSizeException() {
      super("size must be <= 100");
    }
  }
}

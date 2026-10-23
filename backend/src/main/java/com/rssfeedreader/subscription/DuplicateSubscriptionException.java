package com.rssfeedreader.subscription;

import java.io.Serial;

/** A subscription with the same normalized address already exists. */
public final class DuplicateSubscriptionException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public DuplicateSubscriptionException() {
    super("Duplicate subscription");
  }
}

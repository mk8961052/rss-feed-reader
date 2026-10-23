package com.rssfeedreader.subscription;

import java.io.Serial;

/** The submitted address failed {@link SubscriptionUrlValidator}; the message is user-facing. */
public final class InvalidSubscriptionUrlException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public InvalidSubscriptionUrlException(String userMessage) {
    super(userMessage);
  }
}

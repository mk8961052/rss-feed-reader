package com.rssfeedreader.subscription;

import java.time.Instant;
import java.util.UUID;

/** Wire representation of a subscription. Deliberately excludes the normalized URL. */
public record SubscriptionResponse(UUID id, String url, Instant addedAt) {

  static SubscriptionResponse from(Subscription subscription) {
    return new SubscriptionResponse(subscription.id(), subscription.url(), subscription.addedAt());
  }
}

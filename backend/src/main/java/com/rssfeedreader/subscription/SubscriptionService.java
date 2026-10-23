package com.rssfeedreader.subscription;

import java.time.Clock;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionService {

  private final SubscriptionStore store;
  private final Clock clock;

  public SubscriptionService(SubscriptionStore store, Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  /**
   * Validates and stores a new subscription.
   *
   * @throws InvalidSubscriptionUrlException if the address fails the structural check
   * @throws DuplicateSubscriptionException if the address is already subscribed
   */
  public Subscription add(@Nullable String rawUrl) {
    UrlValidationResult result = SubscriptionUrlValidator.validate(rawUrl);
    if (result instanceof UrlValidationResult.Invalid invalid) {
      throw new InvalidSubscriptionUrlException(invalid.errorMessage());
    }
    var valid = (UrlValidationResult.Valid) result;

    var subscription =
        new Subscription(UUID.randomUUID(), valid.url(), valid.normalizedUrl(), clock.instant());
    if (!store.addIfAbsent(subscription)) {
      throw new DuplicateSubscriptionException();
    }
    return subscription;
  }
}

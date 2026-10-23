package com.rssfeedreader.subscription;

import java.util.List;

/** Storage for subscriptions. Implementations must be safe for concurrent use. */
public interface SubscriptionStore {

  /** Returns an immutable snapshot of all subscriptions, oldest first. */
  List<Subscription> findAll();

  /**
   * Adds the subscription unless one with the same {@link Subscription#normalizedUrl()} exists.
   *
   * @return {@code true} if added, {@code false} if it was a duplicate
   */
  boolean addIfAbsent(Subscription subscription);
}

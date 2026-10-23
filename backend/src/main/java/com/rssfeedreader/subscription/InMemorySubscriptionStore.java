package com.rssfeedreader.subscription;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Repository;

/** MVP store: keeps subscriptions in memory for the lifetime of the process. */
@Repository
public final class InMemorySubscriptionStore implements SubscriptionStore {

  private final ReentrantLock lock = new ReentrantLock();
  private final List<Subscription> items = new ArrayList<>();
  private final Set<String> normalizedUrls = new HashSet<>();

  @Override
  public List<Subscription> findAll() {
    lock.lock();
    try {
      return List.copyOf(items);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public boolean addIfAbsent(Subscription subscription) {
    lock.lock();
    try {
      if (!normalizedUrls.add(subscription.normalizedUrl())) {
        return false;
      }
      items.add(subscription);
      return true;
    } finally {
      lock.unlock();
    }
  }
}

package com.rssfeedreader.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InMemorySubscriptionStoreTest {

  private final InMemorySubscriptionStore store = new InMemorySubscriptionStore();

  private static Subscription subscription(String url) {
    return new Subscription(UUID.randomUUID(), url, url, Instant.EPOCH);
  }

  @Test
  void newStoreIsEmpty() {
    assertThat(store.findAll()).isEmpty();
  }

  @Test
  void returnsSubscriptionsInInsertionOrder() {
    var a = subscription("https://a.example/feed");
    var b = subscription("https://b.example/feed");
    var c = subscription("https://c.example/feed");

    store.addIfAbsent(a);
    store.addIfAbsent(b);
    store.addIfAbsent(c);

    assertThat(store.findAll()).containsExactly(a, b, c);
  }

  @Test
  void rejectsDuplicateNormalizedUrl() {
    assertThat(store.addIfAbsent(subscription("https://example.com/feed"))).isTrue();

    assertThat(store.addIfAbsent(subscription("https://example.com/feed"))).isFalse();
    assertThat(store.findAll()).hasSize(1);
  }

  @Test
  void findAllReturnsUnmodifiableSnapshot() {
    store.addIfAbsent(subscription("https://a.example/feed"));
    List<Subscription> snapshot = store.findAll();

    store.addIfAbsent(subscription("https://b.example/feed"));

    assertThat(snapshot).hasSize(1);
    assertThatThrownBy(() -> snapshot.add(subscription("https://c.example/feed")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void concurrentAddsOfSameUrlProduceExactlyOneEntry() throws Exception {
    int threads = 50;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Boolean>> results = new ArrayList<>();
    try {
      for (int i = 0; i < threads; i++) {
        results.add(
            executor.submit(
                () -> {
                  start.await();
                  return store.addIfAbsent(subscription("https://same.example/feed"));
                }));
      }
      start.countDown();

      int added = 0;
      for (Future<Boolean> result : results) {
        if (result.get(10, TimeUnit.SECONDS)) {
          added++;
        }
      }

      assertThat(added).isEqualTo(1);
      assertThat(store.findAll()).hasSize(1);
    } finally {
      executor.shutdownNow();
    }
  }
}

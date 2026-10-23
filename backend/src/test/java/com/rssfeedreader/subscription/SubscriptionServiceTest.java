package com.rssfeedreader.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SubscriptionServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-24T09:00:00Z");

  private final InMemorySubscriptionStore store = new InMemorySubscriptionStore();
  private final SubscriptionService service =
      new SubscriptionService(store, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void addTrimsUrlAndStampsCreationTime() {
    Subscription subscription = service.add("  https://example.org/rss  ");

    assertThat(subscription.url()).isEqualTo("https://example.org/rss");
    assertThat(subscription.id()).isNotNull();
    assertThat(subscription.addedAt()).isEqualTo(NOW);
    assertThat(store.findAll()).containsExactly(subscription);
  }

  @Test
  void addRejectsDuplicateIgnoringHostCase() {
    service.add("https://example.com/feed");

    assertThatThrownBy(() -> service.add("https://EXAMPLE.com/feed"))
        .isInstanceOf(DuplicateSubscriptionException.class);
    assertThat(store.findAll()).hasSize(1);
  }

  @Test
  void addRejectsInvalidUrlWithUserMessage() {
    assertThatThrownBy(() -> service.add("not a url"))
        .isInstanceOf(InvalidSubscriptionUrlException.class)
        .hasMessage(SubscriptionUrlValidator.NOT_WEB_ADDRESS_MESSAGE);
    assertThat(store.findAll()).isEmpty();
  }
}

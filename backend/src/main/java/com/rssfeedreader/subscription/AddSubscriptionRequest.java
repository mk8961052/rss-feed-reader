package com.rssfeedreader.subscription;

import org.jspecify.annotations.Nullable;

/** Body of {@code POST /api/subscriptions}. */
public record AddSubscriptionRequest(@Nullable String url) {}

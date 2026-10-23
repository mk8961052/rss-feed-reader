package com.rssfeedreader.subscription;

import java.time.Instant;
import java.util.UUID;

/**
 * A feed the user has subscribed to.
 *
 * @param id server-assigned identifier
 * @param url user input with surrounding whitespace trimmed; 1–2048 chars; absolute http/https URI
 *     with a host
 * @param normalizedUrl duplicate-detection key; unique across the store; never serialized to
 *     clients
 * @param addedAt creation time (UTC)
 */
public record Subscription(UUID id, String url, String normalizedUrl, Instant addedAt) {}

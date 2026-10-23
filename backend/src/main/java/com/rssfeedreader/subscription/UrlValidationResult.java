package com.rssfeedreader.subscription;

/** Outcome of {@link SubscriptionUrlValidator#validate(String)}. */
public sealed interface UrlValidationResult
    permits UrlValidationResult.Valid, UrlValidationResult.Invalid {

  /**
   * @param url the input with surrounding whitespace trimmed
   * @param normalizedUrl the duplicate-detection key
   */
  record Valid(String url, String normalizedUrl) implements UrlValidationResult {}

  /**
   * @param errorMessage user-facing reason for rejection
   */
  record Invalid(String errorMessage) implements UrlValidationResult {}
}

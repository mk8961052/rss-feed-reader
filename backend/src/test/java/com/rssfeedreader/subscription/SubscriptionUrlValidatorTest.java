package com.rssfeedreader.subscription;

import static com.rssfeedreader.subscription.SubscriptionUrlValidator.EMPTY_MESSAGE;
import static com.rssfeedreader.subscription.SubscriptionUrlValidator.MAX_URL_LENGTH;
import static com.rssfeedreader.subscription.SubscriptionUrlValidator.NOT_WEB_ADDRESS_MESSAGE;
import static com.rssfeedreader.subscription.SubscriptionUrlValidator.TOO_LONG_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SubscriptionUrlValidatorTest {

  private static String urlOfLength(int length) {
    String prefix = "https://example.com/";
    return prefix + "a".repeat(length - prefix.length());
  }

  private static UrlValidationResult.Valid validResult(String input) {
    UrlValidationResult result = SubscriptionUrlValidator.validate(input);
    assertThat(result)
        .as("expected %s to be valid", input)
        .isInstanceOf(UrlValidationResult.Valid.class);
    return (UrlValidationResult.Valid) result;
  }

  private static String errorFor(String input) {
    UrlValidationResult result = SubscriptionUrlValidator.validate(input);
    assertThat(result)
        .as("expected %s to be invalid", input)
        .isInstanceOf(UrlValidationResult.Invalid.class);
    return ((UrlValidationResult.Invalid) result).errorMessage();
  }

  private static String keyOf(String input) {
    return validResult(input).normalizedUrl();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t"})
  void rejectsMissingInput(String input) {
    assertThat(errorFor(input)).isEqualTo(EMPTY_MESSAGE);
  }

  @Test
  void rejectsUrlLongerThanLimit() {
    assertThat(errorFor(urlOfLength(MAX_URL_LENGTH + 1))).isEqualTo(TOO_LONG_MESSAGE);
  }

  @Test
  void acceptsUrlAtLimit() {
    validResult(urlOfLength(MAX_URL_LENGTH));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not a url",
        "ftp://example.com/feed",
        "javascript:alert(1)",
        "/relative/path",
        "mailto:a@b.c",
        "http://"
      })
  void rejectsNonWebAddresses(String input) {
    assertThat(errorFor(input)).isEqualTo(NOT_WEB_ADDRESS_MESSAGE);
  }

  @Test
  void trimsSurroundingWhitespace() {
    assertThat(validResult("  https://example.org/rss  ").url())
        .isEqualTo("https://example.org/rss");
  }

  @Test
  void schemeHostCaseAndDefaultPortDoNotAffectKey() {
    String key = keyOf("https://example.com/feed");

    assertThat(keyOf("HTTPS://EXAMPLE.com/feed")).isEqualTo(key);
    assertThat(keyOf("https://example.com:443/feed")).isEqualTo(key);
  }

  @Test
  void pathCaseAffectsKey() {
    assertThat(keyOf("https://example.com/Feed")).isNotEqualTo(keyOf("https://example.com/feed"));
  }

  @Test
  void nonDefaultPortIsKept() {
    assertThat(keyOf("https://example.com:8443/feed")).contains(":8443");
  }

  @Test
  void emptyPathEqualsRootPath() {
    assertThat(keyOf("https://example.com")).isEqualTo(keyOf("https://example.com/"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://example.com/?q=<b>x</b>", "https://example.com"})
  void acceptsWebAddressesWithoutCheckingFeedValidity(String input) {
    assertThat(validResult(input).url()).isEqualTo(input);
  }
}

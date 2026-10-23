package com.rssfeedreader.subscription;

import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Structural check for subscription URLs (Constitution Principle II). Does not check whether the
 * address is a real feed.
 */
public final class SubscriptionUrlValidator {

  public static final int MAX_URL_LENGTH = 2048;
  public static final String EMPTY_MESSAGE = "Enter a feed address.";
  public static final String TOO_LONG_MESSAGE =
      "The feed address must be " + MAX_URL_LENGTH + " characters or fewer.";
  public static final String NOT_WEB_ADDRESS_MESSAGE =
      "Enter a web address starting with http:// or https://.";

  private static final int HTTP_DEFAULT_PORT = 80;
  private static final int HTTPS_DEFAULT_PORT = 443;

  private SubscriptionUrlValidator() {}

  public static UrlValidationResult validate(@Nullable String input) {
    if (input == null || input.isBlank()) {
      return new UrlValidationResult.Invalid(EMPTY_MESSAGE);
    }
    String trimmed = input.strip();
    if (trimmed.length() > MAX_URL_LENGTH) {
      return new UrlValidationResult.Invalid(TOO_LONG_MESSAGE);
    }

    UriComponents uri;
    try {
      // WHATWG parsing is lenient like a browser, so unencoded characters such as '<' in a
      // query do not cause rejection.
      uri =
          UriComponentsBuilder.fromUriString(trimmed, UriComponentsBuilder.ParserType.WHAT_WG)
              .build();
    } catch (IllegalArgumentException ex) {
      return new UrlValidationResult.Invalid(NOT_WEB_ADDRESS_MESSAGE);
    }

    String scheme = uri.getScheme();
    String host = uri.getHost();
    if (scheme == null || !isWebScheme(scheme) || host == null || host.isBlank()) {
      return new UrlValidationResult.Invalid(NOT_WEB_ADDRESS_MESSAGE);
    }
    return new UrlValidationResult.Valid(trimmed, normalize(uri, scheme, host));
  }

  private static boolean isWebScheme(String scheme) {
    return scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https");
  }

  /** Lowercases scheme and host, drops default ports; path, query and fragment are kept as-is. */
  private static String normalize(UriComponents uri, String scheme, String host) {
    String lowerScheme = scheme.toLowerCase(Locale.ROOT);
    int port = uri.getPort();
    boolean defaultPort =
        port == -1
            || (lowerScheme.equals("http") && port == HTTP_DEFAULT_PORT)
            || (lowerScheme.equals("https") && port == HTTPS_DEFAULT_PORT);
    String path = uri.getPath();
    String query = uri.getQuery();
    String fragment = uri.getFragment();

    StringBuilder key = new StringBuilder();
    key.append(lowerScheme).append("://").append(host.toLowerCase(Locale.ROOT));
    if (!defaultPort) {
      key.append(':').append(port);
    }
    key.append(path == null || path.isEmpty() ? "/" : path);
    if (query != null) {
      key.append('?').append(query);
    }
    if (fragment != null) {
      key.append('#').append(fragment);
    }
    return key.toString();
  }
}

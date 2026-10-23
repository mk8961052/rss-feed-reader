package com.rssfeedreader.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SubscriptionControllerTest {

  private static final String SUBSCRIPTIONS = "/api/subscriptions";
  private static final String UUID_PATTERN =
      "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
  private static final String ISO_INSTANT_PATTERN =
      "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$";

  @Autowired private MockMvc mockMvc;

  private ResultActions postJson(String body) throws Exception {
    return mockMvc.perform(
        post(SUBSCRIPTIONS).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private ResultActions postUrl(String url) throws Exception {
    return postJson("{\"url\":\"" + url + "\"}");
  }

  // --- User Story 1: add a subscription ---

  @Test
  void postValidUrlReturnsCreatedWithTrimmedUrlAndLocation() throws Exception {
    MockHttpServletResponse response =
        postUrl("  https://devblogs.microsoft.com/dotnet/feed/  ")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.url").value("https://devblogs.microsoft.com/dotnet/feed/"))
            .andExpect(jsonPath("$.id").value(matchesPattern(UUID_PATTERN)))
            .andReturn()
            .getResponse();

    String id = JsonPath.read(response.getContentAsString(), "$.id");
    assertThat(response.getHeader("Location")).isEqualTo(SUBSCRIPTIONS + "/" + id);
  }

  @Test
  void postedSubscriptionAppearsInList() throws Exception {
    postUrl("https://devblogs.microsoft.com/dotnet/feed/").andExpect(status().isCreated());

    mockMvc
        .perform(get(SUBSCRIPTIONS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].url").value("https://devblogs.microsoft.com/dotnet/feed/"));
  }

  // --- User Story 2: view the list ---

  @Test
  void emptyStoreReturnsEmptyArray() throws Exception {
    mockMvc
        .perform(get(SUBSCRIPTIONS))
        .andExpect(status().isOk())
        .andExpect(content().json("[]", JsonCompareMode.STRICT));
  }

  @Test
  void listIsReturnedOldestFirst() throws Exception {
    postUrl("https://a.example/feed").andExpect(status().isCreated());
    postUrl("https://b.example/feed").andExpect(status().isCreated());
    postUrl("https://c.example/feed").andExpect(status().isCreated());

    mockMvc
        .perform(get(SUBSCRIPTIONS))
        .andExpect(jsonPath("$[0].url").value("https://a.example/feed"))
        .andExpect(jsonPath("$[1].url").value("https://b.example/feed"))
        .andExpect(jsonPath("$[2].url").value("https://c.example/feed"));
  }

  @Test
  void listItemsExposeOnlyContractFields() throws Exception {
    postUrl("https://example.com/feed").andExpect(status().isCreated());

    mockMvc
        .perform(get(SUBSCRIPTIONS))
        .andExpect(jsonPath("$[0].id").value(matchesPattern(UUID_PATTERN)))
        .andExpect(jsonPath("$[0].url").value("https://example.com/feed"))
        .andExpect(jsonPath("$[0].addedAt").value(matchesPattern(ISO_INSTANT_PATTERN)))
        .andExpect(jsonPath("$[0].normalizedUrl").doesNotExist());
  }

  // --- User Story 3: rejected input ---

  static Stream<Arguments> invalidBodies() {
    String tooLong = "https://example.com/" + "a".repeat(SubscriptionUrlValidator.MAX_URL_LENGTH);
    return Stream.of(
        Arguments.of("{}", SubscriptionUrlValidator.EMPTY_MESSAGE),
        Arguments.of("{\"url\":null}", SubscriptionUrlValidator.EMPTY_MESSAGE),
        Arguments.of("{\"url\":\"\"}", SubscriptionUrlValidator.EMPTY_MESSAGE),
        Arguments.of("{\"url\":\"   \"}", SubscriptionUrlValidator.EMPTY_MESSAGE),
        Arguments.of("{\"url\":\"" + tooLong + "\"}", SubscriptionUrlValidator.TOO_LONG_MESSAGE),
        Arguments.of("{\"url\":\"not a url\"}", SubscriptionUrlValidator.NOT_WEB_ADDRESS_MESSAGE),
        Arguments.of(
            "{\"url\":\"ftp://example.com/feed\"}",
            SubscriptionUrlValidator.NOT_WEB_ADDRESS_MESSAGE),
        Arguments.of(
            "{\"url\":\"javascript:alert(1)\"}", SubscriptionUrlValidator.NOT_WEB_ADDRESS_MESSAGE),
        Arguments.of(
            "{\"url\":\"/relative/path\"}", SubscriptionUrlValidator.NOT_WEB_ADDRESS_MESSAGE));
  }

  @ParameterizedTest
  @MethodSource("invalidBodies")
  void invalidUrlIsRejectedWithProblemDetail(String body, String expectedDetail) throws Exception {
    postJson(body)
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Invalid feed address"))
        .andExpect(jsonPath("$.detail").value(expectedDetail));

    mockMvc.perform(get(SUBSCRIPTIONS)).andExpect(content().json("[]", JsonCompareMode.STRICT));
  }

  @Test
  void duplicateInDifferentHostCaseIsConflict() throws Exception {
    postUrl("https://example.com/feed").andExpect(status().isCreated());

    postUrl("https://EXAMPLE.com/feed")
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Already subscribed"))
        .andExpect(jsonPath("$.detail").value("This feed is already in your subscription list."));
  }

  @Test
  void pathCaseMakesADifferentSubscription() throws Exception {
    postUrl("https://example.com/feed").andExpect(status().isCreated());

    postUrl("https://example.com/Feed").andExpect(status().isCreated());
  }

  @Test
  void urlWithMarkupCharactersIsAccepted() throws Exception {
    postUrl("https://example.com/?q=<b>x</b>")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.url").value("https://example.com/?q=<b>x</b>"));
  }

  @Test
  void malformedJsonIsRejectedWithoutInternalDetails() throws Exception {
    String body =
        postJson("{\"url\":")
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body)
        .doesNotContain("Exception", "\tat ", ".java", "com.rssfeedreader", "tools.jackson");
  }
}

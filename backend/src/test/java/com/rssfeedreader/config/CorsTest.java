package com.rssfeedreader.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class CorsTest {

  private static final String ALLOWED_ORIGIN = "http://localhost:5173";

  @Autowired private MockMvc mockMvc;

  private ResultActions preflightFrom(String origin) throws Exception {
    return mockMvc.perform(
        options("/api/subscriptions")
            .header(HttpHeaders.ORIGIN, origin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"));
  }

  @Test
  void preflightFromFrontendOriginIsAllowed() throws Exception {
    preflightFrom(ALLOWED_ORIGIN)
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
  }

  @Test
  void preflightFromOtherOriginIsRejected() throws Exception {
    preflightFrom("http://evil.example")
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }
}

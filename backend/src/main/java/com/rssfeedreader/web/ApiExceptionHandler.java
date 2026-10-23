package com.rssfeedreader.web;

import com.rssfeedreader.subscription.DuplicateSubscriptionException;
import com.rssfeedreader.subscription.InvalidSubscriptionUrlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps domain exceptions to RFC 9457 problem details. Never exposes exception text or stack traces
 * to clients (Constitution Principle II).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  static final String INVALID_URL_TITLE = "Invalid feed address";
  static final String DUPLICATE_TITLE = "Already subscribed";
  static final String DUPLICATE_DETAIL = "This feed is already in your subscription list.";
  static final String UNEXPECTED_TITLE = "Unexpected error";
  static final String UNEXPECTED_DETAIL = "An unexpected error occurred.";

  @ExceptionHandler(InvalidSubscriptionUrlException.class)
  ProblemDetail handleInvalidUrl(InvalidSubscriptionUrlException ex) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setTitle(INVALID_URL_TITLE);
    return problem;
  }

  @ExceptionHandler(DuplicateSubscriptionException.class)
  ProblemDetail handleDuplicate() {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, DUPLICATE_DETAIL);
    problem.setTitle(DUPLICATE_TITLE);
    return problem;
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    var problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_DETAIL);
    problem.setTitle(UNEXPECTED_TITLE);
    return problem;
  }
}

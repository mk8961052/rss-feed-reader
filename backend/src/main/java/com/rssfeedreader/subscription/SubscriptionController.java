package com.rssfeedreader.subscription;

import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(SubscriptionController.BASE_PATH)
public class SubscriptionController {

  static final String BASE_PATH = "/api/subscriptions";

  private final SubscriptionService service;
  private final SubscriptionStore store;

  public SubscriptionController(SubscriptionService service, SubscriptionStore store) {
    this.service = service;
    this.store = store;
  }

  @GetMapping
  List<SubscriptionResponse> list() {
    return store.findAll().stream().map(SubscriptionResponse::from).toList();
  }

  @PostMapping
  ResponseEntity<SubscriptionResponse> add(@RequestBody AddSubscriptionRequest request) {
    Subscription subscription = service.add(request.url());
    return ResponseEntity.created(URI.create(BASE_PATH + "/" + subscription.id()))
        .body(SubscriptionResponse.from(subscription));
  }
}

package com.rssfeedreader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RssFeedReaderApplication {

  public static void main(String[] args) {
    SpringApplication.run(RssFeedReaderApplication.class, args);
  }
}

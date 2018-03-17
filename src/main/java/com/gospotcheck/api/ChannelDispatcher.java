package com.gospotcheck.api;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.logging.Logger;

public class ChannelDispatcher {
  private ChannelDispatcher() { }

  private static final Logger logger = Logger.getLogger(ChannelDispatcher.class.getName());

  public static ManagedChannel GetForEnvironment(Class serviceClass, String environment) {
    logger.info("class is named: '" + serviceClass.getName() + "'");

    String host;
    int port = 8100;
    ManagedChannel channel;

    if(environment == "prd") {
      host = "api.gospotcheck.com";
    }
    else if(environment == "local") {
      host = "localhost";
    }
    else {
      host = "api-" + environment + ".gospotcheck.com";
    }

    // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
    // needing certificates.
    channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();

    return channel;
  }
}

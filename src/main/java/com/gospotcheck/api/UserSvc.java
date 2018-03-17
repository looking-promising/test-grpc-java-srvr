package com.gospotcheck.api;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UserSvc {
    private static final Logger logger = Logger.getLogger(UserSvc.class.getName());

    private final ManagedChannel channel;
    private final UserSvcGrpc.UserSvcBlockingStub blockingStub;

    /** Construct client connecting to UserSvc server in the specified environment. */
    public UserSvc(String env) {
        this(ChannelDispatcher.GetForEnvironment(UserSvcGrpc.class, env));
    }

    /** Construct client for accessing UserSvc server using the existing channel. */
    public UserSvc(ManagedChannel channel) {
        this.channel = channel;
        blockingStub = UserSvcGrpc.newBlockingStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    /** Say hello to server. */
    public void greet(String name) {
        logger.info("Will try to greet " + name + " ...");
        UserRequest request = UserRequest.newBuilder().setName(name).build();
        UserReply response;
        try {
            response = blockingStub.sayHello(request);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Greeting: " + response.getMessage());
    }
}

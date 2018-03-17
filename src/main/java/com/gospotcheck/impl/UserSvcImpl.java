package com.gospotcheck.impl;

import com.gospotcheck.api.UserReply;
import com.gospotcheck.api.UserRequest;
import com.gospotcheck.api.UserSvcGrpc;
import io.grpc.stub.StreamObserver;

import java.util.logging.Logger;

public class UserSvcImpl extends UserSvcGrpc.UserSvcImplBase {
    private static final Logger logger = Logger.getLogger(UserSvcImpl.class.getName());

    @Override
    public void sayHello(UserRequest req, StreamObserver<UserReply> responseObserver) {
        logger.info("Saying hello to " + req.getName());

        UserReply reply = UserReply.newBuilder().setMessage("User " + req.getName()).build();
        responseObserver.onNext(reply);

        logger.info("DONE: Saying hello to " + req.getName());
        responseObserver.onCompleted();
    }
}

/*
 * Copyright 2015, gRPC Authors All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gospotcheck.api;

import com.gospotcheck.app.PlatformServicesServerTest;
import com.gospotcheck.app.UserConsumerExample;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcServerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;

import static org.junit.Assert.assertEquals;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link UserConsumerExample}.
 * For demonstrating how to write gRPC unit test only.
 * Not intended to provide a high code coverage or to test every major usecase.
 * <p>
 * <p>For more unit test examples see {@link CompanySvcTest} and
 * {@link PlatformServicesServerTest}.
 */
@RunWith(JUnit4.class)
public class UserSvcTest {
    /**
     * This creates and starts an in-process server, and creates a client with an in-process channel.
     * When the test is done, it also shuts down the in-process client and server.
     */
    @Rule
    public final GrpcServerRule grpcServerRule = new GrpcServerRule().directExecutor();
    private final UserSvcGrpc.UserSvcImplBase serviceImpl =
            mock(UserSvcGrpc.UserSvcImplBase.class, delegatesTo(new UserSvcGrpc.UserSvcImplBase() {
            }));

    private UserSvc client;

    @Before
    public void setUp() throws Exception {
        // Add service.
        grpcServerRule.getServiceRegistry().addService(serviceImpl);

        // Create a UserConsumerExample using the in-process channel;
        client = new UserSvc(grpcServerRule.getChannel());
    }

    /**
     * To test the client, call from the client against the fake server, and verify behaviors or state
     * changes from the server side.
     */
    @Test
    public void greet_messageDeliveredToServer() {
        ArgumentCaptor<UserRequest> requestCaptor = ArgumentCaptor.forClass(UserRequest.class);
        String testName = "test name";

        client.greet(testName);

        verify(serviceImpl)
                .sayHello(requestCaptor.capture(), Matchers.<StreamObserver<UserReply>>any());
        assertEquals(testName, requestCaptor.getValue().getName());
    }
}

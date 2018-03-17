/*
 * Copyright 2016, gRPC Authors All rights reserved.
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

import com.google.protobuf.Message;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.util.MutableHandlerRegistry;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CompanySvc}.
 * For demonstrating how to write gRPC unit test only.
 * Not intended to provide a high code coverage or to test every major usecase.
 * <p>
 * <p>For basic unit test examples see {@link UserSvcTest}.
 */
@RunWith(JUnit4.class)
public class CompanySvcTest {
    private final MutableHandlerRegistry serviceRegistry = new MutableHandlerRegistry();
    private final CompanySvc.TestHelper testHelper = mock(CompanySvc.TestHelper.class);
    private final Random noRandomness =
            new Random() {
                int index;
                boolean isForSleep;

                /**
                 * Returns a number deterministically. If the random number is for sleep time, then return
                 * -500 so that {@code Thred.sleep(random.nextInt(1000) + 500)} sleeps 0 ms. Otherwise, it
                 * is for list index, then return incrementally (and cyclically).
                 */
                @Override
                public int nextInt(int bound) {
                    int retVal = isForSleep ? -500 : (index++ % bound);
                    isForSleep = !isForSleep;
                    return retVal;
                }
            };
    private Server fakeServer;
    private CompanySvc client;

    @Before
    public void setUp() throws Exception {
        String uniqueServerName = "fake server for " + getClass();

        // use a mutable service registry for later registering the service impl for each test case.
        fakeServer = InProcessServerBuilder.forName(uniqueServerName)
                .fallbackHandlerRegistry(serviceRegistry).directExecutor().build().start();

        ManagedChannel channel = InProcessChannelBuilder.forName(uniqueServerName).directExecutor().build();
        client = new CompanySvc(channel);
        client.setTestHelper(testHelper);
    }

    @After
    public void tearDown() throws Exception {
        client.shutdown();
        fakeServer.shutdownNow();
    }

    /**
     * Example for testing blocking unary call.
     */
    @Test
    public void getCompanyFeature() {
        CompanyPoint requestCompanyPoint = CompanyPoint.newBuilder().setLatitude(-1).setLongitude(-1).build();
        CompanyPoint responseCompanyPoint = CompanyPoint.newBuilder().setLatitude(-123).setLongitude(-123).build();
        final AtomicReference<CompanyPoint> pointDelivered = new AtomicReference<CompanyPoint>();
        final CompanyFeature responseCompanyFeature =
                CompanyFeature.newBuilder().setName("dummyCompanyFeature").setLocation(responseCompanyPoint).build();

        // implement the fake service
        CompanySvcGrpc.CompanySvcImplBase getCompanyFeatureImpl =
                new CompanySvcGrpc.CompanySvcImplBase() {
                    @Override
                    public void getCompanyFeature(CompanyPoint point, StreamObserver<CompanyFeature> responseObserver) {
                        pointDelivered.set(point);
                        responseObserver.onNext(responseCompanyFeature);
                        responseObserver.onCompleted();
                    }
                };
        serviceRegistry.addService(getCompanyFeatureImpl);

        client.getCompanyFeature(-1, -1);

        assertEquals(requestCompanyPoint, pointDelivered.get());
        verify(testHelper).onMessage(responseCompanyFeature);
        verify(testHelper, never()).onRpcError(any(Throwable.class));
    }

    /**
     * Example for testing blocking unary call.
     */
    @Test
    public void getCompanyFeature_error() {
        CompanyPoint requestCompanyPoint = CompanyPoint.newBuilder().setLatitude(-1).setLongitude(-1).build();
        final AtomicReference<CompanyPoint> pointDelivered = new AtomicReference<CompanyPoint>();
        final StatusRuntimeException fakeError = new StatusRuntimeException(Status.DATA_LOSS);

        // implement the fake service
        CompanySvcGrpc.CompanySvcImplBase getCompanyFeatureImpl =
                new CompanySvcGrpc.CompanySvcImplBase() {
                    @Override
                    public void getCompanyFeature(CompanyPoint point, StreamObserver<CompanyFeature> responseObserver) {
                        pointDelivered.set(point);
                        responseObserver.onError(fakeError);
                    }
                };
        serviceRegistry.addService(getCompanyFeatureImpl);

        client.getCompanyFeature(-1, -1);

        assertEquals(requestCompanyPoint, pointDelivered.get());
        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(testHelper).onRpcError(errorCaptor.capture());
        assertEquals(fakeError.getStatus(), Status.fromThrowable(errorCaptor.getValue()));
    }

    /**
     * Example for testing blocking server-streaming.
     */
    @Test
    public void listCompanyFeatures() {
        final CompanyFeature responseCompanyFeature1 = CompanyFeature.newBuilder().setName("feature 1").build();
        final CompanyFeature responseCompanyFeature2 = CompanyFeature.newBuilder().setName("feature 2").build();
        final AtomicReference<CompanyRectangle> rectangleDelivered = new AtomicReference<CompanyRectangle>();

        // implement the fake service
        CompanySvcGrpc.CompanySvcImplBase listCompanyFeaturesImpl =
                new CompanySvcGrpc.CompanySvcImplBase() {
                    @Override
                    public void listCompanyFeatures(CompanyRectangle rectangle, StreamObserver<CompanyFeature> responseObserver) {
                        rectangleDelivered.set(rectangle);

                        // send two response messages
                        responseObserver.onNext(responseCompanyFeature1);
                        responseObserver.onNext(responseCompanyFeature2);

                        // complete the response
                        responseObserver.onCompleted();
                    }
                };
        serviceRegistry.addService(listCompanyFeaturesImpl);

        client.listCompanyFeatures(1, 2, 3, 4);

        Assert.assertEquals(CompanyRectangle.newBuilder()
                        .setLo(CompanyPoint.newBuilder().setLatitude(1).setLongitude(2).build())
                        .setHi(CompanyPoint.newBuilder().setLatitude(3).setLongitude(4).build())
                        .build(),
                rectangleDelivered.get());
        verify(testHelper).onMessage(responseCompanyFeature1);
        verify(testHelper).onMessage(responseCompanyFeature2);
        verify(testHelper, never()).onRpcError(any(Throwable.class));
    }

    /**
     * Example for testing blocking server-streaming.
     */
    @Test
    public void listCompanyFeatures_error() {
        final CompanyFeature responseCompanyFeature1 =
                CompanyFeature.newBuilder().setName("feature 1").build();
        final AtomicReference<CompanyRectangle> rectangleDelivered = new AtomicReference<CompanyRectangle>();
        final StatusRuntimeException fakeError = new StatusRuntimeException(Status.INVALID_ARGUMENT);

        // implement the fake service
        CompanySvcGrpc.CompanySvcImplBase listCompanyFeaturesImpl =
                new CompanySvcGrpc.CompanySvcImplBase() {
                    @Override
                    public void listCompanyFeatures(CompanyRectangle rectangle, StreamObserver<CompanyFeature> responseObserver) {
                        rectangleDelivered.set(rectangle);

                        // send one response message
                        responseObserver.onNext(responseCompanyFeature1);

                        // let the rpc fail
                        responseObserver.onError(fakeError);
                    }
                };
        serviceRegistry.addService(listCompanyFeaturesImpl);

        client.listCompanyFeatures(1, 2, 3, 4);

        Assert.assertEquals(CompanyRectangle.newBuilder()
                        .setLo(CompanyPoint.newBuilder().setLatitude(1).setLongitude(2).build())
                        .setHi(CompanyPoint.newBuilder().setLatitude(3).setLongitude(4).build())
                        .build(),
                rectangleDelivered.get());
        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(testHelper).onMessage(responseCompanyFeature1);
        verify(testHelper).onRpcError(errorCaptor.capture());
        assertEquals(fakeError.getStatus(), Status.fromThrowable(errorCaptor.getValue()));
    }

    /**
     * Example for testing async client-streaming.
     */
    @Test
    public void recordRoute() throws Exception {
        client.setRandom(noRandomness);
        CompanyPoint point1 = CompanyPoint.newBuilder().setLatitude(1).setLongitude(1).build();
        CompanyPoint point2 = CompanyPoint.newBuilder().setLatitude(2).setLongitude(2).build();
        CompanyPoint point3 = CompanyPoint.newBuilder().setLatitude(3).setLongitude(3).build();
        CompanyFeature requestCompanyFeature1 =
                CompanyFeature.newBuilder().setLocation(point1).build();
        CompanyFeature requestCompanyFeature2 =
                CompanyFeature.newBuilder().setLocation(point2).build();
        CompanyFeature requestCompanyFeature3 =
                CompanyFeature.newBuilder().setLocation(point3).build();
        final List<CompanyFeature> features = Arrays.asList(
                requestCompanyFeature1, requestCompanyFeature2, requestCompanyFeature3);
        final List<CompanyPoint> pointsDelivered = new ArrayList<CompanyPoint>();
        final CompanyRouteSummary fakeResponse = CompanyRouteSummary
                .newBuilder()
                .setPointCount(7)
                .setFeatureCount(8)
                .setDistance(9)
                .setElapsedTime(10)
                .build();

        // implement the fake service
        CompanySvcGrpc.CompanySvcImplBase recordRouteImpl =
                new CompanySvcGrpc.CompanySvcImplBase() {
                    @Override
                    public StreamObserver<CompanyPoint> recordRoute(
                            final StreamObserver<CompanyRouteSummary> responseObserver) {
                        StreamObserver<CompanyPoint> requestObserver = new StreamObserver<CompanyPoint>() {
                            @Override
                            public void onNext(CompanyPoint value) {
                                pointsDelivered.add(value);
                            }

                            @Override
                            public void onError(Throwable t) {
                            }

                            @Override
                            public void onCompleted() {
                                responseObserver.onNext(fakeResponse);
                                responseObserver.onCompleted();
                            }
                        };

                        return requestObserver;
                    }
                };
        serviceRegistry.addService(recordRouteImpl);

        // send requestCompanyFeature1, requestCompanyFeature2, requestCompanyFeature3, and then requestCompanyFeature1 again
        client.recordRoute(features, 4);

        assertEquals(
                Arrays.asList(
                        requestCompanyFeature1.getLocation(),
                        requestCompanyFeature2.getLocation(),
                        requestCompanyFeature3.getLocation(),
                        requestCompanyFeature1.getLocation()),
                pointsDelivered);
        verify(testHelper).onMessage(fakeResponse);
        verify(testHelper, never()).onRpcError(any(Throwable.class));
    }

    /**
     * Example for testing async client-streaming.
     */
    @Test
    public void recordRoute_serverError() throws Exception {
        client.setRandom(noRandomness);
        CompanyPoint point1 = CompanyPoint.newBuilder().setLatitude(1).setLongitude(1).build();
        final CompanyFeature requestCompanyFeature1 =
                CompanyFeature.newBuilder().setLocation(point1).build();
        final List<CompanyFeature> features = Arrays.asList(requestCompanyFeature1);
        final StatusRuntimeException fakeError = new StatusRuntimeException(Status.INVALID_ARGUMENT);

        // implement the fake service
        CompanySvcGrpc.CompanySvcImplBase recordRouteImpl =
                new CompanySvcGrpc.CompanySvcImplBase() {
                    @Override
                    public StreamObserver<CompanyPoint> recordRoute(StreamObserver<CompanyRouteSummary> responseObserver) {
                        // send an error immediately
                        responseObserver.onError(fakeError);

                        StreamObserver<CompanyPoint> requestObserver = new StreamObserver<CompanyPoint>() {
                            @Override
                            public void onNext(CompanyPoint value) {
                            }

                            @Override
                            public void onError(Throwable t) {
                            }

                            @Override
                            public void onCompleted() {
                            }
                        };
                        return requestObserver;
                    }
                };
        serviceRegistry.addService(recordRouteImpl);

        client.recordRoute(features, 4);

        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(testHelper).onRpcError(errorCaptor.capture());
        assertEquals(fakeError.getStatus(), Status.fromThrowable(errorCaptor.getValue()));
    }

    /**
     * Example for testing bi-directional call.
     */
    @Test
    public void routeChat_simpleResponse() throws Exception {
        CompanyRouteNote fakeResponse1 = CompanyRouteNote.newBuilder().setMessage("dummy msg1").build();
        CompanyRouteNote fakeResponse2 = CompanyRouteNote.newBuilder().setMessage("dummy msg2").build();
        final List<String> messagesDelivered = new ArrayList<String>();
        final List<CompanyPoint> locationsDelivered = new ArrayList<CompanyPoint>();
        final AtomicReference<StreamObserver<CompanyRouteNote>> responseObserverRef =
                new AtomicReference<StreamObserver<CompanyRouteNote>>();
        final CountDownLatch allRequestsDelivered = new CountDownLatch(1);
        // implement the fake service
        CompanySvcGrpc.CompanySvcImplBase routeChatImpl =
                new CompanySvcGrpc.CompanySvcImplBase() {
                    @Override
                    public StreamObserver<CompanyRouteNote> routeChat(StreamObserver<CompanyRouteNote> responseObserver) {
                        responseObserverRef.set(responseObserver);

                        StreamObserver<CompanyRouteNote> requestObserver = new StreamObserver<CompanyRouteNote>() {
                            @Override
                            public void onNext(CompanyRouteNote value) {
                                messagesDelivered.add(value.getMessage());
                                locationsDelivered.add(value.getLocation());
                            }

                            @Override
                            public void onError(Throwable t) {
                            }

                            @Override
                            public void onCompleted() {
                                allRequestsDelivered.countDown();
                            }
                        };

                        return requestObserver;
                    }
                };
        serviceRegistry.addService(routeChatImpl);

        // start routeChat
        CountDownLatch latch = client.routeChat();

        // request message sent and delivered for four times
        assertTrue(allRequestsDelivered.await(1, TimeUnit.SECONDS));
        assertEquals(
                Arrays.asList("First message", "Second message", "Third message", "Fourth message"),
                messagesDelivered);
        assertEquals(
                Arrays.asList(
                        CompanyPoint.newBuilder().setLatitude(0).setLongitude(0).build(),
                        CompanyPoint.newBuilder().setLatitude(0).setLongitude(1).build(),
                        CompanyPoint.newBuilder().setLatitude(1).setLongitude(0).build(),
                        CompanyPoint.newBuilder().setLatitude(1).setLongitude(1).build()
                ),
                locationsDelivered);

        // Let the server send out two simple response messages
        // and verify that the client receives them.
        // Allow some timeout for verify() if not using directExecutor
        responseObserverRef.get().onNext(fakeResponse1);
        verify(testHelper).onMessage(fakeResponse1);
        responseObserverRef.get().onNext(fakeResponse2);
        verify(testHelper).onMessage(fakeResponse2);

        // let server complete.
        responseObserverRef.get().onCompleted();

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        verify(testHelper, never()).onRpcError(any(Throwable.class));
    }

    /**
     * Example for testing bi-directional call.
     */
    @Test
    public void routeChat_echoResponse() throws Exception {
        final List<CompanyRouteNote> notesDelivered = new ArrayList<CompanyRouteNote>();

        // implement the fake service
        CompanySvcGrpc.CompanySvcImplBase routeChatImpl =
                new CompanySvcGrpc.CompanySvcImplBase() {
                    @Override
                    public StreamObserver<CompanyRouteNote> routeChat(
                            final StreamObserver<CompanyRouteNote> responseObserver) {
                        StreamObserver<CompanyRouteNote> requestObserver = new StreamObserver<CompanyRouteNote>() {
                            @Override
                            public void onNext(CompanyRouteNote value) {
                                notesDelivered.add(value);
                                responseObserver.onNext(value);
                            }

                            @Override
                            public void onError(Throwable t) {
                                responseObserver.onError(t);
                            }

                            @Override
                            public void onCompleted() {
                                responseObserver.onCompleted();
                            }
                        };

                        return requestObserver;
                    }
                };
        serviceRegistry.addService(routeChatImpl);

        client.routeChat().await(1, TimeUnit.SECONDS);

        String[] messages =
                {"First message", "Second message", "Third message", "Fourth message"};
        for (int i = 0; i < 4; i++) {
            verify(testHelper).onMessage(notesDelivered.get(i));
            assertEquals(messages[i], notesDelivered.get(i).getMessage());
        }

        verify(testHelper, never()).onRpcError(any(Throwable.class));
    }

    /**
     * Example for testing bi-directional call.
     */
    @Test
    public void routeChat_errorResponse() throws Exception {
        final List<CompanyRouteNote> notesDelivered = new ArrayList<CompanyRouteNote>();
        final StatusRuntimeException fakeError = new StatusRuntimeException(Status.PERMISSION_DENIED);

        // implement the fake service
        CompanySvcGrpc.CompanySvcImplBase routeChatImpl =
                new CompanySvcGrpc.CompanySvcImplBase() {
                    @Override
                    public StreamObserver<CompanyRouteNote> routeChat(
                            final StreamObserver<CompanyRouteNote> responseObserver) {
                        StreamObserver<CompanyRouteNote> requestObserver = new StreamObserver<CompanyRouteNote>() {
                            @Override
                            public void onNext(CompanyRouteNote value) {
                                notesDelivered.add(value);
                                responseObserver.onError(fakeError);
                            }

                            @Override
                            public void onError(Throwable t) {
                            }

                            @Override
                            public void onCompleted() {
                                responseObserver.onCompleted();
                            }
                        };

                        return requestObserver;
                    }
                };
        serviceRegistry.addService(routeChatImpl);

        client.routeChat().await(1, TimeUnit.SECONDS);

        assertEquals("First message", notesDelivered.get(0).getMessage());
        verify(testHelper, never()).onMessage(any(Message.class));
        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(testHelper).onRpcError(errorCaptor.capture());
        assertEquals(fakeError.getStatus(), Status.fromThrowable(errorCaptor.getValue()));
    }
}

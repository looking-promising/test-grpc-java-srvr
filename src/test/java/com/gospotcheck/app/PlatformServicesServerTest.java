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

package com.gospotcheck.app;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.gospotcheck.api.*;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link PlatformServicesServer}.
 * For demonstrating how to write gRPC unit test only.
 * Not intended to provide a high code coverage or to test every major usecase.
 *
 * <p>For basic unit test examples see {@link UserSvcTest} and
 * {@link UserSvcTest}.
 */
@RunWith(JUnit4.class)
public class PlatformServicesServerTest {
  private PlatformServicesServer server;
  private ManagedChannel inProcessChannel;
  private Collection<CompanyFeature> features;

  @Before
  public void setUp() throws Exception {
    String uniqueServerName = "in-process server for " + getClass();
    features = new ArrayList<CompanyFeature>();
    // use directExecutor for both InProcessServerBuilder and InProcessChannelBuilder can reduce the
    // usage timeouts and latches in test. But we still add timeout and latches where they would be
    // needed if no directExecutor were used, just for demo purpose.
    server = new PlatformServicesServer(
        InProcessServerBuilder.forName(uniqueServerName).directExecutor(), 0, features);
    server.start();
    inProcessChannel = InProcessChannelBuilder.forName(uniqueServerName).directExecutor().build();
  }

  @After
  public void tearDown() throws Exception {
    inProcessChannel.shutdownNow();
    server.stop();
  }

  /**
   * To test the server, make calls with a real stub using the in-process channel, and verify
   * behaviors or state changes from the client side.
   */
  @Test
  public void UserSvcImpl_replyMessage() throws Exception {
    UserSvcGrpc.UserSvcBlockingStub stub = UserSvcGrpc.newBlockingStub(inProcessChannel);

    String testName = "test name";

    UserReply reply = stub.sayHello(UserRequest.newBuilder().setName(testName).build());

    assertEquals("User " + testName, reply.getMessage());
  }

  @Test
  public void CompanySvc_getFeature() {
    CompanyPoint point = CompanyPoint.newBuilder().setLongitude(1).setLatitude(1).build();
    CompanyFeature unnamedFeature = CompanyFeature.newBuilder()
        .setName("").setLocation(point).build();
    CompanySvcGrpc.CompanySvcBlockingStub stub = CompanySvcGrpc.newBlockingStub(inProcessChannel);

    // feature not found in the server
    CompanyFeature feature = stub.getCompanyFeature(point);

    assertEquals(unnamedFeature, feature);

    // feature found in the server
    CompanyFeature namedFeature = CompanyFeature.newBuilder()
        .setName("name").setLocation(point).build();
    features.add(namedFeature);

    feature = stub.getCompanyFeature(point);

    assertEquals(namedFeature, feature);
  }

  @Test
  public void CompanySvc_listFeatures() throws Exception {
    // setup
    CompanyRectangle rect = CompanyRectangle.newBuilder()
        .setLo(CompanyPoint.newBuilder().setLongitude(0).setLatitude(0).build())
        .setHi(CompanyPoint.newBuilder().setLongitude(10).setLatitude(10).build())
        .build();
    CompanyFeature f1 = CompanyFeature.newBuilder()
        .setLocation(CompanyPoint.newBuilder().setLongitude(-1).setLatitude(-1).build())
        .setName("f1")
        .build(); // not inside rect
    CompanyFeature f2 = CompanyFeature.newBuilder()
        .setLocation(CompanyPoint.newBuilder().setLongitude(2).setLatitude(2).build())
        .setName("f2")
        .build();
    CompanyFeature f3 = CompanyFeature.newBuilder()
        .setLocation(CompanyPoint.newBuilder().setLongitude(3).setLatitude(3).build())
        .setName("f3")
        .build();
    CompanyFeature f4 = CompanyFeature.newBuilder()
        .setLocation(CompanyPoint.newBuilder().setLongitude(4).setLatitude(4).build())
        .build(); // unamed
    features.add(f1);
    features.add(f2);
    features.add(f3);
    features.add(f4);
    final Collection<CompanyFeature> result = new HashSet<CompanyFeature>();
    final CountDownLatch latch = new CountDownLatch(1);
    StreamObserver<CompanyFeature> responseObserver =
        new StreamObserver<CompanyFeature>() {
          @Override
          public void onNext(CompanyFeature value) {
            result.add(value);
          }

          @Override
          public void onError(Throwable t) {
            fail();
          }

          @Override
          public void onCompleted() {
            latch.countDown();
          }
        };
    CompanySvcGrpc.CompanySvcStub stub = CompanySvcGrpc.newStub(inProcessChannel);

    // run
    stub.listCompanyFeatures(rect, responseObserver);
    assertTrue(latch.await(1, TimeUnit.SECONDS));

    // verify
    assertEquals(new HashSet<CompanyFeature>(Arrays.asList(f2, f3)), result);
  }

  @Test
  public void CompanySvc_recordRoute() {
    CompanyPoint p1 = CompanyPoint.newBuilder().setLongitude(1000).setLatitude(1000).build();
    CompanyPoint p2 = CompanyPoint.newBuilder().setLongitude(2000).setLatitude(2000).build();
    CompanyPoint p3 = CompanyPoint.newBuilder().setLongitude(3000).setLatitude(3000).build();
    CompanyPoint p4 = CompanyPoint.newBuilder().setLongitude(4000).setLatitude(4000).build();
    CompanyFeature f1 = CompanyFeature.newBuilder().setLocation(p1).build(); // unamed
    CompanyFeature f2 = CompanyFeature.newBuilder().setLocation(p2).setName("f2").build();
    CompanyFeature f3 = CompanyFeature.newBuilder().setLocation(p3).setName("f3").build();
    CompanyFeature f4 = CompanyFeature.newBuilder().setLocation(p4).build(); // unamed
    features.add(f1);
    features.add(f2);
    features.add(f3);
    features.add(f4);

    @SuppressWarnings("unchecked")
    StreamObserver<CompanyRouteSummary> responseObserver =
        (StreamObserver<CompanyRouteSummary>) mock(StreamObserver.class);
    CompanySvcGrpc.CompanySvcStub stub = CompanySvcGrpc.newStub(inProcessChannel);
    ArgumentCaptor<CompanyRouteSummary> routeSummaryCaptor = ArgumentCaptor.forClass(CompanyRouteSummary.class);

    StreamObserver<CompanyPoint> requestObserver = stub.recordRoute(responseObserver);

    requestObserver.onNext(p1);
    requestObserver.onNext(p2);
    requestObserver.onNext(p3);
    requestObserver.onNext(p4);

    verify(responseObserver, never()).onNext(any(CompanyRouteSummary.class));

    requestObserver.onCompleted();

    // allow some ms to let client receive the response. Similar usage later on.
    verify(responseObserver, timeout(100)).onNext(routeSummaryCaptor.capture());
    CompanyRouteSummary summary = routeSummaryCaptor.getValue();
    assertEquals(45, summary.getDistance()); // 45 is the hard coded distance from p1 to p4.
    assertEquals(2, summary.getFeatureCount());
    verify(responseObserver, timeout(100)).onCompleted();
    verify(responseObserver, never()).onError(any(Throwable.class));
  }

  @Test
  public void CompanySvc_routeChat() {
    CompanyPoint p1 = CompanyPoint.newBuilder().setLongitude(1).setLatitude(1).build();
    CompanyPoint p2 = CompanyPoint.newBuilder().setLongitude(2).setLatitude(2).build();
    CompanyRouteNote n1 = CompanyRouteNote.newBuilder().setLocation(p1).setMessage("m1").build();
    CompanyRouteNote n2 = CompanyRouteNote.newBuilder().setLocation(p2).setMessage("m2").build();
    CompanyRouteNote n3 = CompanyRouteNote.newBuilder().setLocation(p1).setMessage("m3").build();
    CompanyRouteNote n4 = CompanyRouteNote.newBuilder().setLocation(p2).setMessage("m4").build();
    CompanyRouteNote n5 = CompanyRouteNote.newBuilder().setLocation(p1).setMessage("m5").build();
    CompanyRouteNote n6 = CompanyRouteNote.newBuilder().setLocation(p1).setMessage("m6").build();
    int timesOnNext = 0;

    @SuppressWarnings("unchecked")
    StreamObserver<CompanyRouteNote> responseObserver =
        (StreamObserver<CompanyRouteNote>) mock(StreamObserver.class);
    CompanySvcGrpc.CompanySvcStub stub = CompanySvcGrpc.newStub(inProcessChannel);

    StreamObserver<CompanyRouteNote> requestObserver = stub.routeChat(responseObserver);
    verify(responseObserver, never()).onNext(any(CompanyRouteNote.class));

    requestObserver.onNext(n1);
    verify(responseObserver, never()).onNext(any(CompanyRouteNote.class));

    requestObserver.onNext(n2);
    verify(responseObserver, never()).onNext(any(CompanyRouteNote.class));

    requestObserver.onNext(n3);
    ArgumentCaptor<CompanyRouteNote> routeNoteCaptor = ArgumentCaptor.forClass(CompanyRouteNote.class);
    verify(responseObserver, timeout(100).times(++timesOnNext)).onNext(routeNoteCaptor.capture());
    CompanyRouteNote result = routeNoteCaptor.getValue();
    assertEquals(p1, result.getLocation());
    assertEquals("m1", result.getMessage());

    requestObserver.onNext(n4);
    routeNoteCaptor = ArgumentCaptor.forClass(CompanyRouteNote.class);
    verify(responseObserver, timeout(100).times(++timesOnNext)).onNext(routeNoteCaptor.capture());
    result = routeNoteCaptor.getAllValues().get(timesOnNext - 1);
    assertEquals(p2, result.getLocation());
    assertEquals("m2", result.getMessage());

    requestObserver.onNext(n5);
    routeNoteCaptor = ArgumentCaptor.forClass(CompanyRouteNote.class);
    timesOnNext += 2;
    verify(responseObserver, timeout(100).times(timesOnNext)).onNext(routeNoteCaptor.capture());
    result = routeNoteCaptor.getAllValues().get(timesOnNext - 2);
    assertEquals(p1, result.getLocation());
    assertEquals("m1", result.getMessage());
    result = routeNoteCaptor.getAllValues().get(timesOnNext - 1);
    assertEquals(p1, result.getLocation());
    assertEquals("m3", result.getMessage());

    requestObserver.onNext(n6);
    routeNoteCaptor = ArgumentCaptor.forClass(CompanyRouteNote.class);
    timesOnNext += 3;
    verify(responseObserver, timeout(100).times(timesOnNext)).onNext(routeNoteCaptor.capture());
    result = routeNoteCaptor.getAllValues().get(timesOnNext - 3);
    assertEquals(p1, result.getLocation());
    assertEquals("m1", result.getMessage());
    result = routeNoteCaptor.getAllValues().get(timesOnNext - 2);
    assertEquals(p1, result.getLocation());
    assertEquals("m3", result.getMessage());
    result = routeNoteCaptor.getAllValues().get(timesOnNext - 1);
    assertEquals(p1, result.getLocation());
    assertEquals("m5", result.getMessage());

    requestObserver.onCompleted();
    verify(responseObserver, timeout(100)).onCompleted();
    verify(responseObserver, never()).onError(any(Throwable.class));
  }
}

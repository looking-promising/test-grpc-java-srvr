package com.gospotcheck.api;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.Message;
import com.gospotcheck.app.CompanyConsumerExample;
import com.gospotcheck.impl.CompanySvcImpl;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CompanySvc {
    private static final Logger logger = Logger.getLogger(CompanyConsumerExample.class.getName());

    private final ManagedChannel channel;
    private final CompanySvcGrpc.CompanySvcBlockingStub blockingStub;
    private final CompanySvcGrpc.CompanySvcStub asyncStub;

    private Random random = new Random();
    private CompanySvc.TestHelper testHelper;

    /**
     * Construct client demo for accessing PlatformServices server at {@code host:port}.
     */
    public CompanySvc(String env) {
        this(ChannelDispatcher.GetForEnvironment(CompanySvcGrpc.class, env));
    }

    /**
     * Construct client demo for accessing PlatformServices server using the existing channel.
     */
    public CompanySvc(ManagedChannel channel) {
        if (channel == null) throw new NullPointerException("channel cannot be null");

        this.channel = channel;
        this.blockingStub = CompanySvcGrpc.newBlockingStub(channel);
        this.asyncStub = CompanySvcGrpc.newStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * Blocking unary call example.  Calls getCompanyFeature and prints the response.
     */
    public void getCompanyFeature(int lat, int lon) {
        info("*** GetCompanyFeature: lat={0} lon={1}", lat, lon);

        CompanyPoint request = CompanyPoint.newBuilder().setLatitude(lat).setLongitude(lon).build();

        CompanyFeature feature;
        try {
            feature = blockingStub.getCompanyFeature(request);
            if (testHelper != null) {
                testHelper.onMessage(feature);
            }
        } catch (StatusRuntimeException e) {
            warning("RPC failed: {0}", e.getStatus());
            if (testHelper != null) {
                testHelper.onRpcError(e);
            }
            return;
        }

        if (CompanySvcImpl.Util.exists(feature)) {
            info("Found feature called \"{0}\" at {1}, {2}",
                    feature.getName(),
                    CompanySvcImpl.Util.getLatitude(feature.getLocation()),
                    CompanySvcImpl.Util.getLongitude(feature.getLocation()));
        } else {
            info("Found no feature at {0}, {1}",
                    CompanySvcImpl.Util.getLatitude(feature.getLocation()),
                    CompanySvcImpl.Util.getLongitude(feature.getLocation()));
        }
    }

    /**
     * Blocking server-streaming example. Calls listCompanyFeatures with a rectangle of interest. Prints each
     * response feature as it arrives.
     */
    public void listCompanyFeatures(int lowLat, int lowLon, int hiLat, int hiLon) {
        info("*** ListCompanyFeatures: lowLat={0} lowLon={1} hiLat={2} hiLon={3}", lowLat, lowLon, hiLat,
                hiLon);

        CompanyRectangle request =
                CompanyRectangle.newBuilder()
                        .setLo(CompanyPoint.newBuilder().setLatitude(lowLat).setLongitude(lowLon).build())
                        .setHi(CompanyPoint.newBuilder().setLatitude(hiLat).setLongitude(hiLon).build()).build();
        Iterator<CompanyFeature> features;
        try {
            features = blockingStub.listCompanyFeatures(request);
            for (int i = 1; features.hasNext(); i++) {
                CompanyFeature feature = features.next();
                info("Result #" + i + ": {0}", feature);
                if (testHelper != null) {
                    testHelper.onMessage(feature);
                }
            }
        } catch (StatusRuntimeException e) {
            warning("RPC failed: {0}", e.getStatus());
            if (testHelper != null) {
                testHelper.onRpcError(e);
            }
        }
    }

    /**
     * Async client-streaming example. Sends {@code numCompanyPoints} randomly chosen points from {@code
     * features} with a variable delay in between. Prints the statistics when they are sent from the
     * server.
     */
    public void recordRoute(List<CompanyFeature> features, int numCompanyPoints) throws InterruptedException {
        info("*** RecordRoute");
        final CountDownLatch finishLatch = new CountDownLatch(1);
        StreamObserver<CompanyRouteSummary> responseObserver = new StreamObserver<CompanyRouteSummary>() {
            @Override
            public void onNext(CompanyRouteSummary summary) {
                info("Finished trip with {0} points. Passed {1} features. "
                                + "Travelled {2} meters. It took {3} seconds.", summary.getPointCount(),
                        summary.getFeatureCount(), summary.getDistance(), summary.getElapsedTime());
                if (testHelper != null) {
                    testHelper.onMessage(summary);
                }
            }

            @Override
            public void onError(Throwable t) {
                warning("RecordRoute Failed: {0}", Status.fromThrowable(t));
                if (testHelper != null) {
                    testHelper.onRpcError(t);
                }
                finishLatch.countDown();
            }

            @Override
            public void onCompleted() {
                info("Finished RecordRoute");
                finishLatch.countDown();
            }
        };

        StreamObserver<CompanyPoint> requestObserver = asyncStub.recordRoute(responseObserver);
        try {
            // Send numCompanyPoints points randomly selected from the features list.
            for (int i = 0; i < numCompanyPoints; ++i) {
                int index = random.nextInt(features.size());
                CompanyPoint point = features.get(index).getLocation();
                info("Visiting point {0}, {1}", CompanySvcImpl.Util.getLatitude(point),
                        CompanySvcImpl.Util.getLongitude(point));
                requestObserver.onNext(point);
                // Sleep for a bit before sending the next one.
                Thread.sleep(random.nextInt(1000) + 500);
                if (finishLatch.getCount() == 0) {
                    // RPC completed or errored before we finished sending.
                    // Sending further requests won't error, but they will just be thrown away.
                    return;
                }
            }
        } catch (RuntimeException e) {
            // Cancel RPC
            requestObserver.onError(e);
            throw e;
        }
        // Mark the end of requests
        requestObserver.onCompleted();

        // Receiving happens asynchronously
        if (!finishLatch.await(1, TimeUnit.MINUTES)) {
            warning("recordRoute can not finish within 1 minutes");
        }
    }

    /**
     * Bi-directional example, which can only be asynchronous. Send some chat messages, and print any
     * chat messages that are sent from the server.
     */
    public CountDownLatch routeChat() {
        info("*** RouteChat");
        final CountDownLatch finishLatch = new CountDownLatch(1);
        StreamObserver<CompanyRouteNote> requestObserver =
                asyncStub.routeChat(new StreamObserver<CompanyRouteNote>() {
                    @Override
                    public void onNext(CompanyRouteNote note) {
                        info("Got message \"{0}\" at {1}, {2}", note.getMessage(), note.getLocation()
                                .getLatitude(), note.getLocation().getLongitude());
                        if (testHelper != null) {
                            testHelper.onMessage(note);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        warning("RouteChat Failed: {0}", Status.fromThrowable(t));
                        if (testHelper != null) {
                            testHelper.onRpcError(t);
                        }
                        finishLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        info("Finished RouteChat");
                        finishLatch.countDown();
                    }
                });

        try {
            CompanyRouteNote[] requests =
                    {newNote("First message", 0, 0), newNote("Second message", 0, 1),
                            newNote("Third message", 1, 0), newNote("Fourth message", 1, 1)};

            for (CompanyRouteNote request : requests) {
                info("Sending message \"{0}\" at {1}, {2}", request.getMessage(), request.getLocation()
                        .getLatitude(), request.getLocation().getLongitude());
                requestObserver.onNext(request);
            }
        } catch (RuntimeException e) {
            // Cancel RPC
            requestObserver.onError(e);
            throw e;
        }
        // Mark the end of requests
        requestObserver.onCompleted();

        // return the latch while receiving happens asynchronously
        return finishLatch;
    }

    private void info(String msg, Object... params) {
        logger.log(Level.INFO, msg, params);
    }

    private void warning(String msg, Object... params) {
        logger.log(Level.WARNING, msg, params);
    }

    private CompanyRouteNote newNote(String message, int lat, int lon) {
        return CompanyRouteNote.newBuilder().setMessage(message)
                .setLocation(CompanyPoint.newBuilder().setLatitude(lat).setLongitude(lon).build()).build();
    }

    /**
     * Only used for unit test, as we do not want to introduce randomness in unit test.
     */
    @VisibleForTesting
    public void setRandom(Random random) {
        this.random = random;
    }

    @VisibleForTesting
    public void setTestHelper(CompanySvc.TestHelper testHelper) {
        this.testHelper = testHelper;
    }

    /**
     * Only used for helping unit test.
     */
    @VisibleForTesting
    public interface TestHelper {
        /**
         * Used for verify/inspect message received from server.
         */
        void onMessage(Message message);

        /**
         * Used for verify/inspect error received from server.
         */
        void onRpcError(Throwable exception);
    }
}

package com.gospotcheck.impl;

import com.google.protobuf.util.JsonFormat;
import com.gospotcheck.app.PlatformServicesServer;
import com.gospotcheck.api.*;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Math.*;
import static java.lang.Math.sqrt;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Our implementation of RouteGuide service.
 *
 * <p>See company.proto for details of the methods.
 */
public class CompanySvcImpl extends CompanySvcGrpc.CompanySvcImplBase {
    private static final Logger logger = Logger.getLogger(CompanySvcImpl.class.getName());
    private final Collection<CompanyFeature> features;
    private final ConcurrentMap<CompanyPoint, List<CompanyRouteNote>> routeNotes =
            new ConcurrentHashMap<CompanyPoint, List<CompanyRouteNote>>();

    public CompanySvcImpl(Collection<CompanyFeature> features) {
        this.features = features;
    }

    /**
     * Gets the {@link CompanyFeature} at the requested {@link CompanyPoint}. If no feature at that location
     * exists, an unnamed feature is returned at the provided location.
     *
     * @param request the requested location for the feature.
     * @param responseObserver the observer that will receive the feature at the requested point.
     */
    @Override
    public void getCompanyFeature(CompanyPoint request, StreamObserver<CompanyFeature> responseObserver) {
        logger.info("Getting Company Feataure");
        responseObserver.onNext(checkCompanyFeature(request));

        logger.info("DONE: Getting Company Feataure");
        responseObserver.onCompleted();
    }

    /**
     * Gets all features contained within the given bounding {@link CompanyRectangle}.
     *
     * @param request the bounding rectangle for the requested features.
     * @param responseObserver the observer that will receive the features.
     */
    @Override
    public void listCompanyFeatures(CompanyRectangle request, StreamObserver<CompanyFeature> responseObserver) {
        logger.info("Listing Company Features");
        int left = Math.min(request.getLo().getLongitude(), request.getHi().getLongitude());
        int right = Math.max(request.getLo().getLongitude(), request.getHi().getLongitude());
        int top = Math.max(request.getLo().getLatitude(), request.getHi().getLatitude());
        int bottom = Math.min(request.getLo().getLatitude(), request.getHi().getLatitude());

        for (CompanyFeature feature : features) {
            if (!Util.exists(feature)) {
                continue;
            }

            int lat = feature.getLocation().getLatitude();
            int lon = feature.getLocation().getLongitude();
            if (lon >= left && lon <= right && lat >= bottom && lat <= top) {
                responseObserver.onNext(feature);
            }
        }

        logger.info("DONE: Listing Company Features");
        responseObserver.onCompleted();
    }

    /**
     * Gets a stream of points, and responds with statistics about the "trip": number of points,
     * number of known features visited, total distance traveled, and total time spent.
     *
     * @param responseObserver an observer to receive the response summary.
     * @return an observer to receive the requested route points.
     */
    @Override
    public StreamObserver<CompanyPoint> recordRoute(final StreamObserver<CompanyRouteSummary> responseObserver) {
        return new StreamObserver<CompanyPoint>() {
            int pointCount;
            int featureCount;
            int distance;
            CompanyPoint previous;
            final long startTime = System.nanoTime();

            @Override
            public void onNext(CompanyPoint point) {
                logger.info("Recording Route");

                pointCount++;
                if (Util.exists(checkCompanyFeature(point))) {
                    featureCount++;
                }
                // For each point after the first, add the incremental distance from the previous point to
                // the total distance value.
                if (previous != null) {
                    distance += calcDistance(previous, point);
                }
                previous = point;
            }

            @Override
            public void onError(Throwable t) {
                logger.log(Level.WARNING, "recordRoute cancelled");
            }

            @Override
            public void onCompleted() {
                long seconds = NANOSECONDS.toSeconds(System.nanoTime() - startTime);

                responseObserver
                        .onNext(CompanyRouteSummary.newBuilder().setPointCount(pointCount)
                        .setFeatureCount(featureCount).setDistance(distance)
                        .setElapsedTime((int) seconds).build());

                logger.info("DONE: Recording Route");
                responseObserver.onCompleted();
            }
        };
    }

    /**
     * Receives a stream of message/location pairs, and responds with a stream of all previous
     * messages at each of those locations.
     *
     * @param responseObserver an observer to receive the stream of previous messages.
     * @return an observer to handle requested message/location pairs.
     */
    @Override
    public StreamObserver<CompanyRouteNote> routeChat(final StreamObserver<CompanyRouteNote> responseObserver) {
        return new StreamObserver<CompanyRouteNote>() {
            @Override
            public void onNext(CompanyRouteNote note) {
                logger.info("routeChat");

                List<CompanyRouteNote> notes = getOrCreateNotes(note.getLocation());

                // Respond with all previous notes at this location.
                for (CompanyRouteNote prevNote : notes.toArray(new CompanyRouteNote[0])) {
                    responseObserver.onNext(prevNote);
                }

                // Now add the new note to the list
                notes.add(note);
            }

            @Override
            public void onError(Throwable t) {
                logger.log(Level.WARNING, "routeChat cancelled");
            }

            @Override
            public void onCompleted() {
                logger.info("DONE: routeChat");

                responseObserver.onCompleted();
            }
        };
    }

    /**
     * Get the notes list for the given location. If missing, create it.
     */
    private List<CompanyRouteNote> getOrCreateNotes(CompanyPoint location) {
        List<CompanyRouteNote> notes = Collections.synchronizedList(new ArrayList<CompanyRouteNote>());
        List<CompanyRouteNote> prevNotes = routeNotes.putIfAbsent(location, notes);
        return prevNotes != null ? prevNotes : notes;
    }

    /**
     * Gets the feature at the given point.
     *
     * @param location the location to check.
     * @return The feature object at the point. Note that an empty name indicates no feature.
     */
    private CompanyFeature checkCompanyFeature(CompanyPoint location) {
        for (CompanyFeature feature : features) {
            if (feature.getLocation().getLatitude() == location.getLatitude()
                    && feature.getLocation().getLongitude() == location.getLongitude()) {
                return feature;
            }
        }

        // No feature was found, return an unnamed feature.
        return CompanyFeature.newBuilder().setName("").setLocation(location).build();
    }

    /**
     * Calculate the distance between two points using the "haversine" formula.
     * This code was taken from http://www.movable-type.co.uk/scripts/latlong.html.
     *
     * @param start The starting point
     * @param end The end point
     * @return The distance between the points in meters
     */
    private static int calcDistance(CompanyPoint start, CompanyPoint end) {
        double lat1 = Util.getLatitude(start);
        double lat2 = Util.getLatitude(end);
        double lon1 = Util.getLongitude(start);
        double lon2 = Util.getLongitude(end);
        int r = 6371000; // meters
        double phi1 = toRadians(lat1);
        double phi2 = toRadians(lat2);
        double deltaPhi = toRadians(lat2 - lat1);
        double deltaLambda = toRadians(lon2 - lon1);

        double a = sin(deltaPhi / 2) * sin(deltaPhi / 2)
                + cos(phi1) * cos(phi2) * sin(deltaLambda / 2) * sin(deltaLambda / 2);
        double c = 2 * atan2(sqrt(a), sqrt(1 - a));

        return (int) (r * c);
    }

    public static class Util {
        private static final double COORD_FACTOR = 1e7;

        /**
         * Gets the latitude for the given point.
         */
        public static double getLatitude(CompanyPoint location) {
            return location.getLatitude() / COORD_FACTOR;
        }

        /**
         * Gets the longitude for the given point.
         */
        public static double getLongitude(CompanyPoint location) {
            return location.getLongitude() / COORD_FACTOR;
        }

        /**
         * Gets the default features file from classpath.
         */
        public static URL getDefaultCompanyFeaturesFile() {
            return PlatformServicesServer.class.getResource("route_guide_db.json");
        }

        /**
         * Parses the JSON input file containing the list of features.
         */
        public static List<CompanyFeature> parseCompanyFeatures(URL file) throws IOException {
            InputStream input = file.openStream();
            try {
                Reader reader = new InputStreamReader(input, Charset.forName("UTF-8"));
                try {
                    CompanyFeatureDatabase.Builder pretendDatabase = CompanyFeatureDatabase.newBuilder();
                    JsonFormat.parser().merge(reader, pretendDatabase);

                    return pretendDatabase.getFeatureList();
                } finally {
                    reader.close();
                }
            } finally {
                input.close();
            }
        }

        /**
         * Indicates whether the given feature exists (i.e. has a valid name).
         */
        public static boolean exists(CompanyFeature feature) {
            return feature != null && !feature.getName().isEmpty();
        }
    }
}

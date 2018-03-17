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

package com.gospotcheck.app;

import com.gospotcheck.api.CompanyFeature;
import com.gospotcheck.impl.CompanySvcImpl;
import com.gospotcheck.impl.UserSvcImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * A sample gRPC server that serve the RouteGuide (see company.proto) service.
 */
public class PlatformServicesServer {
    private static final Logger logger = Logger.getLogger(PlatformServicesServer.class.getName());

    private final int port;
    private final Server server;

    public PlatformServicesServer(int port) throws IOException {
        this(port, CompanySvcImpl.Util.getDefaultCompanyFeaturesFile());
    }

    /**
     * Create a RouteGuide server listening on {@code port} using {@code featureFile} database.
     */
    public PlatformServicesServer(int port, URL featureFile) throws IOException {
        this(ServerBuilder.forPort(port), port, CompanySvcImpl.Util.parseCompanyFeatures(featureFile));
    }

    /**
     * Create a RouteGuide server using serverBuilder as a base and features as data.
     */
    public PlatformServicesServer(ServerBuilder<?> serverBuilder, int port, Collection<CompanyFeature> features) {
        this.port = port;
        server = serverBuilder
                .addService(new CompanySvcImpl(features))
                .addService(new UserSvcImpl())
                .build();
    }

    /**
     * Main method.  This comment makes the linter happy.
     */
    public static void main(String[] args) throws Exception {
        PlatformServicesServer server = new PlatformServicesServer(8100);
        server.start();
        server.blockUntilShutdown();
    }

    /**
     * Start serving requests.
     */
    public void start() throws IOException {
        server.start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may has been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                PlatformServicesServer.this.stop();
                System.err.println("*** server shut down");
            }
        });
    }

    /**
     * Stop serving requests and shutdown resources.
     */
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }


}

package com.gospotcheck.app;

import com.gospotcheck.api.CompanyFeature;
import com.gospotcheck.api.CompanySvc;
import com.gospotcheck.impl.CompanySvcImpl;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CompanyConsumerExample {
    private static final Logger logger = Logger.getLogger(CompanyConsumerExample.class.getName());

    /**
     * Issues several different requests and then exits.
     */
    public static void main(String[] args) throws InterruptedException {
        String env = "local";
        List<CompanyFeature> features;

        try {

            if (args.length > 0) {
                env = args[0]; /* Use the arg to specify environment */
            }

            features = CompanySvcImpl.Util.parseCompanyFeatures(CompanySvcImpl.Util.getDefaultCompanyFeaturesFile());
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }

        CompanySvc client = new CompanySvc(env);
        try {
            // Looking for a valid feature
            client.getCompanyFeature(409146138, -746188906);

            // CompanyFeature missing.
            client.getCompanyFeature(0, 0);

            // Looking for features between 40, -75 and 42, -73.
            client.listCompanyFeatures(400000000, -750000000, 420000000, -730000000);

            // Record a few randomly selected points from the features file.
            client.recordRoute(features, 10);

            // Send and receive some notes.
            CountDownLatch finishLatch = client.routeChat();

            if (!finishLatch.await(1, TimeUnit.MINUTES)) {
                CompanyConsumerExample.warning("routeChat can not finish within 1 minutes");
            }
        } finally {
            client.shutdown();
        }
    }

    private static void info(String msg, Object... params) {
        logger.log(Level.INFO, msg, params);
    }

    private static void warning(String msg, Object... params) {
        logger.log(Level.WARNING, msg, params);
    }
}

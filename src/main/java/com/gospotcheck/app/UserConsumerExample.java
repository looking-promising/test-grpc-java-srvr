package com.gospotcheck.app;

import com.gospotcheck.api.UserSvc;

import java.util.logging.Logger;

/**
 * A simple client that requests a user from the {@link UserSvc}.
 */
public class UserConsumerExample {
    private static final Logger logger = Logger.getLogger(UserConsumerExample.class.getName());

    /**
     * Greet server. If provided, the first element of {@code args} is the name to use in the
     * greeting.
     */
    public static void main(String[] args) throws Exception {
        UserSvc client = new UserSvc("local");

        try {
            String user = "Doctor";

            if (args.length > 0) {
                user = args[0]; /* Use the arg as the name to greet if provided */
            }
            client.greet(user);

        } finally {
            client.shutdown();
        }
    }
}

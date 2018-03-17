grpc Examples
==============================================

The examples require grpc-java to already be built. You are strongly encouraged
to check out a git release tag, since there will already be a build of grpc
available. Otherwise you must follow [COMPILING](../COMPILING.md).

You may want to read through the
[Quick Start Guide](https://grpc.io/docs/quickstart/java.html)
before trying out the examples.

To build the examples, run in this directory:

```
$ ./gradlew installDist
```

This creates the scripts `platform-services-server`, `usersvc_consumer-demo`,
`companysvc-consumer-demo` in the `build/install/test-grpc-java/bin/` directory 
that run the demos. Each demo requires the server to be running before 
starting the client.

Run the server:
```
$ build/install/test-grpc-java/bin/platform-services-server
```

And in a different terminal window run:
```
$ build/install/test-grpc-java/bin/usersvc-consumer-demo
```
or
```
$ build/install/test-grpc-java/bin/companysvc-consumer-demo
```


Please refer to gRPC Java's [README](../README.md) and
[tutorial](https://grpc.io/docs/tutorials/basic/java.html) for more
information.


Unit test examples
==============================================

Examples for unit testing gRPC clients and servers are located in [examples/src/test](src/test).

In general, we DO NOT allow overriding the client stub.
We encourage users to leverage `InProcessTransport` as demonstrated in the examples to
write unit tests. `InProcessTransport` is light-weight and runs the server
and client in the same process without any socket/TCP connection.

For testing a gRPC client, create the client with a real stub
using an
[InProcessChannel](../core/src/main/java/io/grpc/inprocess/InProcessChannelBuilder.java),
and test it against an
[InProcessServer](../core/src/main/java/io/grpc/inprocess/InProcessServerBuilder.java)
with a mock/fake service implementation.

For testing a gRPC server, create the server as an InProcessServer,
and test it against a real client stub with an InProcessChannel.

The gRPC-java library also provides a JUnit rule,
[GrpcServerRule](../testing/src/main/java/io/grpc/testing/GrpcServerRule.java), to do the starting
up and shutting down boilerplate for you.

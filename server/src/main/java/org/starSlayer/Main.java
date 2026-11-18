package org.starSlayer;

import java.net.InetSocketAddress;

public class Main {


    public static String host = "0.0.0.0";
    public static int port = 8081;

    static void main() throws Exception {
        webSocketServer server = new webSocketServer(new InetSocketAddress(host, port));
        server.start();
        DockerRunner dockerRunner = new DockerRunner();

    }
}

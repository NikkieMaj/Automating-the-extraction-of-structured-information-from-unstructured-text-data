package com.reviewer.web;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class WebServer {

    private final int port;
    private HttpServer server;

    public WebServer(int port) { this.port = port; }

    public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new PageHandler());
        server.createContext("/analyze", new AnalyzeHandler());
        server.createContext("/history", new HistoryHandler());
        server.createContext("/connect", new ConnectHandler());
        server.createContext("/profiles", new ProfilesHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }
}

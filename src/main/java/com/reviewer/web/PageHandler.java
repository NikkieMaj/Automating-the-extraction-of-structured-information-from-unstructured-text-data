package com.reviewer.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;

public class PageHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        Path htmlPath = Path.of("index.html");
        String html;
        if (Files.exists(htmlPath)) {
            html = Files.readString(htmlPath, StandardCharsets.UTF_8);
        } else {
            html = "<h1>Файл index.html не найден в " + htmlPath.toAbsolutePath() + "</h1>";
        }

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }
}

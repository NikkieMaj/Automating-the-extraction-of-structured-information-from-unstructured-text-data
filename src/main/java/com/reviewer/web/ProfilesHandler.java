package com.reviewer.web;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class ProfilesHandler implements HttpHandler {

    private final Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        String path   = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("OPTIONS".equals(method)) {
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
            return;
        }

        try {
            if ("GET".equals(method)) {
                handleGet(exchange);
            } else if ("POST".equals(method) && path.endsWith("/switch")) {
                handleSwitch(exchange);
            } else if ("POST".equals(method) && path.endsWith("/delete")) {
                handleDelete(exchange);
            } else if ("POST".equals(method)) {
                handleAdd(exchange);
            } else {
                send(exchange, 405, "{\"error\":\"Method not allowed\"}");
            }
        } catch (Exception e) {
            send(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        long telegramId = getLongParam(exchange, "telegramId");
        if (telegramId == 0) {
            send(exchange, 400, "{\"error\":\"telegramId required\"}");
            return;
        }
        JsonArray  profiles = ProfileManager.get().getProfiles(telegramId);
        JsonObject user = ProfileManager.get().getUserByTelegramId(telegramId);
        JsonObject resp = new JsonObject();
        resp.add("profiles", profiles);
        resp.addProperty("activeProfileId",
                user != null ? user.get("activeProfileId").getAsString() : "default");
        send(exchange, 200, resp.toString());
    }

    private void handleAdd(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        JsonObject req = gson.fromJson(body, JsonObject.class);
        long telegramId = req.get("telegramId").getAsLong();
        String name = req.get("name").getAsString().trim();
        if (name.isEmpty()) {
            send(exchange, 400, "{\"error\":\"Имя профиля не может быть пустым\"}");
            return;
        }
        boolean ok = ProfileManager.get().addProfile(telegramId, name);
        send(exchange, 200, "{\"ok\":" + ok + "}");
    }

    private void handleSwitch(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        JsonObject req  = gson.fromJson(body, JsonObject.class);
        long telegramId = req.get("telegramId").getAsLong();
        String profileId  = req.get("profileId").getAsString();
        boolean ok = ProfileManager.get().switchProfile(telegramId, profileId);
        send(exchange, 200, "{\"ok\":" + ok + "}");
    }

    private void handleDelete(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        JsonObject req  = gson.fromJson(body, JsonObject.class);
        long telegramId = req.get("telegramId").getAsLong();
        String profileId  = req.get("profileId").getAsString();
        if ("default".equals(profileId)) {
            send(exchange, 400, "{\"error\":\"Основной профиль нельзя удалить\"}");
            return;
        }
        boolean ok = ProfileManager.get().deleteProfile(telegramId, profileId);
        send(exchange, 200, "{\"ok\":" + ok + "}");
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private long getLongParam(HttpExchange exchange, String name) {
        try {
            String query = exchange.getRequestURI().getQuery();
            if (query == null) return 0;
            for (String p : query.split("&"))
                if (p.startsWith(name + "="))
                    return Long.parseLong(p.substring(name.length() + 1));
        } catch (Exception ignored) {}
        return 0;
    }

    private void send(HttpExchange ex, int code, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }
}

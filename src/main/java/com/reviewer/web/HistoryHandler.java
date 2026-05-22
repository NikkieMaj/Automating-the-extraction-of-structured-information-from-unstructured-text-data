package com.reviewer.web;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class HistoryHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        try {
            String query = exchange.getRequestURI().getQuery();
            long telegramId = getLong(query, "telegramId");
            String profileId = getStr(query, "profileId", "default");
            int page = getInt(query, "page", 0);
            int pageSize = getInt(query, "pageSize", 5);

            com.google.gson.JsonObject result;

            if (telegramId != 0) {
                result = ProfileManager.get().getHistoryPage(telegramId, profileId, page, pageSize);
                System.out.printf("[HIST] user=%d profile=%s page=%d items=%d%n",
                    telegramId, profileId, page, result.get("total").getAsInt());
            } else {
                result = legacyHistory(page, pageSize);
            }

            byte[] b = result.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, b.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(b); }

        } catch (Exception e) {
            byte[] b = ("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, b.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(b); }
        }
    }

    private long getLong(String query, String key) {
        try {
            if (query == null) return 0;
            for (String p : query.split("&"))
                if (p.startsWith(key + "=")) return Long.parseLong(p.substring(key.length() + 1));
        } catch (Exception ignored) {}
        return 0;
    }

    private int getInt(String query, String key, int def) {
        try {
            if (query == null) return def;
            for (String p : query.split("&"))
                if (p.startsWith(key + "=")) return Integer.parseInt(p.substring(key.length() + 1));
        } catch (Exception ignored) {}
        return def;
    }

    private String getStr(String query, String key, String def) {
        try {
            if (query == null) return def;
            for (String p : query.split("&"))
                if (p.startsWith(key + "=")) return p.substring(key.length() + 1);
        } catch (Exception ignored) {}
        return def;
    }

    private com.google.gson.JsonObject legacyHistory(int page, int pageSize) {
        com.google.gson.JsonArray all = HistoryManager.load();
        int total = all.size();
        int from = page * pageSize;
        int to = Math.min(from + pageSize, total);
        com.google.gson.JsonArray items = new com.google.gson.JsonArray();
        for (int i = from; i < to; i++) items.add(all.get(i));
        com.google.gson.JsonObject r = new com.google.gson.JsonObject();
        r.add("items", items);
        r.addProperty("total", total);
        r.addProperty("page",  page);
        r.addProperty("pages", (int) Math.ceil((double) total / Math.max(1, pageSize)));
        return r;
    }
}

package com.reviewer.web;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;


public class ConnectHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        String query = exchange.getRequestURI().getQuery();
        String code = null;
        if (query != null) {
            for (String p : query.split("&")) {
                if (p.startsWith("code=")) { code = p.substring(5); break; }
            }
        }

        JsonObject resp = new JsonObject();
        if (code == null || code.isBlank()) {
            resp.addProperty("error", "Код не указан");
        } else {
            JsonObject user = ProfileManager.get().getUserByCode(code.toUpperCase());
            if (user == null) {
                resp.addProperty("error", "Неверный код. Получи актуальный код командой /code в боте.");
            } else {
                resp.addProperty("ok",true);
                resp.addProperty("telegramId", user.get("telegramId").getAsLong());
                resp.addProperty("firstName", user.get("firstName").getAsString());
                resp.addProperty("activeProfileId", user.get("activeProfileId").getAsString());
                resp.add("profiles", user.getAsJsonArray("profiles"));
            }
        }

        byte[] bytes = resp.toString().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }
}

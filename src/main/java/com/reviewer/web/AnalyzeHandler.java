package com.reviewer.web;

import com.google.gson.*;
import com.reviewer.Main;
import com.reviewer.model.AnalysisResult;
import com.reviewer.model.Review;
import com.reviewer.parser.WildberriesParser;
import com.reviewer.service.AiService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AnalyzeHandler implements HttpHandler {

    private final Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");

        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, 0); exchange.close(); return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed"); return;
        }

        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonObject req = gson.fromJson(body, JsonObject.class);

            String productId = req.get("productId").getAsString().trim();
            long telegramId = req.has("telegramId") ? req.get("telegramId").getAsLong() : 0;
            String profileId = req.has("profileId")  ? req.get("profileId").getAsString() : "default";

            if (productId.isEmpty()) {sendError(exchange, 400, "Артикул не указан"); return;}

            System.out.printf("[WEB] Анализ: %s (user=%d, profile=%s)%n", productId, telegramId, profileId);

            List<Review> reviews = new WildberriesParser().parseReviews(productId, Main.MAX_REVIEWS);
            if (reviews.isEmpty()) { sendError(exchange, 500, "Отзывы не найдены. Проверьте артикул."); return;}

            AnalysisResult result = new AiService(Main.GROQ_API_KEY, "").analyze(reviews);

            System.out.printf("[WEB] Сохраняем: telegramId=%d, profileId=%s%n", telegramId, profileId);
            if (telegramId != 0) {
                ProfileManager.get().saveAnalysis(telegramId, profileId, productId, result);
            }

            HistoryManager.save(productId, "wb", result);

            JsonObject response = new JsonObject();
            response.addProperty("productName", result.getProductName());
            response.addProperty("reviewCount", result.getReviewCount());
            response.addProperty("summary", result.getSummary());

            JsonArray pros = new JsonArray(); result.getPros().forEach(pros::add);
            response.add("pros", pros);
            JsonArray cons = new JsonArray(); result.getCons().forEach(cons::add);
            response.add("cons", cons);

            sendJson(exchange, 200, response.toString());

        } catch (Exception e) {
            System.err.println("[WEB] Ошибка: " + e.getMessage());
            e.printStackTrace();
            sendError(exchange, 500, e.getMessage());
        }
    }

    private void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }
    private void sendError(HttpExchange ex, int code, String msg) throws IOException {
        JsonObject err = new JsonObject(); err.addProperty("error", msg);
        sendJson(ex, code, err.toString());
    }
}

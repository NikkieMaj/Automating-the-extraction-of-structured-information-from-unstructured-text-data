package com.reviewer.web;

import com.google.gson.*;
import com.reviewer.model.AnalysisResult;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class HistoryManager {

    private static final String HISTORY_FILE = "history.json";
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void save(String productId, String marketplace, AnalysisResult result) {
        try {
            JsonArray history = load();

            JsonObject entry = new JsonObject();
            entry.addProperty("date", LocalDateTime.now().format(FMT));
            entry.addProperty("marketplace", marketplace);
            entry.addProperty("productId", productId);
            entry.addProperty("productName", result.getProductName());
            entry.addProperty("reviewCount", result.getReviewCount());
            entry.addProperty("summary", result.getSummary());

            JsonArray pros = new JsonArray();
            result.getPros().forEach(pros::add);
            entry.add("pros", pros);

            JsonArray cons = new JsonArray();
            result.getCons().forEach(cons::add);
            entry.add("cons", cons);

            JsonArray updated = new JsonArray();
            updated.add(entry);
            history.forEach(updated::add);

            Files.writeString(Path.of(HISTORY_FILE),
                    GSON.toJson(updated), StandardCharsets.UTF_8);

        } catch (Exception e) {
            System.err.println("[History] Не удалось сохранить: " + e.getMessage());
        }
    }

    public static JsonArray load() {
        try {
            if (!Files.exists(Path.of(HISTORY_FILE))) return new JsonArray();
            String content = Files.readString(Path.of(HISTORY_FILE), StandardCharsets.UTF_8);
            return JsonParser.parseString(content).getAsJsonArray();
        } catch (Exception e) {
            return new JsonArray();
        }
    }
}

package com.reviewer.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reviewer.model.AnalysisResult;
import com.reviewer.model.Review;
import com.reviewer.util.HttpUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class AiService {

    private static final String API_URL =
            "https://api.groq.com/openai/v1/chat/completions";

    private final String apiKey;

    public AiService(String apiKey, String folderId) {
        this.apiKey = apiKey;
    }

    public AnalysisResult analyze(List<Review> reviews) throws Exception {
        if (reviews.isEmpty()) throw new IllegalArgumentException("Список отзывов пуст");

        String reviewsText = reviews.stream()
                .map(r -> String.format("Оценка %d/5: %s", r.getRating(), r.getText()))
                .collect(Collectors.joining("\n---\n"));

        String userPrompt = String.format("""
                Проанализируй отзывы покупателей на товар «%s» с Wildberries.
                
                Отзывы:
                %s
                
                Ответь СТРОГО в формате JSON (без лишнего текста и без markdown):
                {
                  "pros": ["плюс 1", "плюс 2", "плюс 3"],
                  "cons": ["минус 1", "минус 2", "минус 3"],
                  "summary": "Краткий общий вывод в одном предложении"
                }
                
                Выдели 3-5 наиболее часто упоминаемых плюсов и минусов.
                Основывайся только на содержании отзывов.
                """,
                reviews.get(0).getProductName(), reviewsText
        );

        JsonObject body = new JsonObject();
        body.addProperty("model", "llama-3.3-70b-versatile");
        body.addProperty("temperature", 0.2);
        body.addProperty("max_tokens", 1000);

        JsonArray messages = new JsonArray();

        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content",
            "Ты аналитик отзывов. Отвечай только в формате JSON без markdown-обёртки.");
        messages.add(system);

        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.addProperty("content", userPrompt);
        messages.add(user);

        body.add("messages", messages);

        String response = HttpUtil.post(API_URL, body.toString(),
                Map.of("Authorization", "Bearer " + apiKey));

        return parseResponse(response, reviews);
    }

    private AnalysisResult parseResponse(String raw, List<Review> reviews) {
        JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
        String text = root.getAsJsonArray("choices")
                          .get(0).getAsJsonObject()
                          .getAsJsonObject("message")
                          .get("content").getAsString().trim();

        text = text.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").trim();

        JsonObject gpt = JsonParser.parseString(text).getAsJsonObject();

        AnalysisResult ar = new AnalysisResult();
        ar.setProductName(reviews.get(0).getProductName());
        ar.setReviewCount(reviews.size());
        ar.setAverageRating(reviews.stream().mapToInt(Review::getRating).average().orElse(0));
        ar.setPros(toList(gpt.getAsJsonArray("pros")));
        ar.setCons(toList(gpt.getAsJsonArray("cons")));
        ar.setSummary(gpt.get("summary").getAsString());

        return ar;
    }

    private List<String> toList(JsonArray arr) {
        List<String> list = new ArrayList<>();
        if (arr != null) arr.forEach(e -> list.add(e.getAsString()));
        return list;
    }
}

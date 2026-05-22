package com.reviewer.service;

import com.reviewer.model.AnalysisResult;
import com.reviewer.model.Review;
import com.reviewer.parser.WildberriesParser;

import java.util.List;

public class ReviewAnalyzer {

    private final WildberriesParser parser;
    private final AiService         aiService;
    private final int               maxReviews;

    public ReviewAnalyzer(WildberriesParser parser, AiService aiService, int maxReviews) {
        this.parser     = parser;
        this.aiService  = aiService;
        this.maxReviews = maxReviews;
    }

    public AnalysisResult analyze(String productId) {
        try {
            List<Review> reviews = parser.parseReviews(productId, maxReviews);

            if (reviews.isEmpty()) {
                System.out.println("[WARN] Отзывы не найдены. Проверьте артикул товара.");
                return null;
            }

            System.out.printf("[AI] Анализируем %d отзывов...%n", reviews.size());
            return aiService.analyze(reviews);

        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}

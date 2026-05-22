package com.reviewer.model;

import java.util.List;

public class AnalysisResult {
    private String       productName;
    private int          reviewCount;
    private double       averageRating;
    private List<String> pros;
    private List<String> cons;
    private String       summary;

    public String       getProductName()           { return productName; }
    public void         setProductName(String v)   { this.productName = v; }
    public int          getReviewCount()           { return reviewCount; }
    public void         setReviewCount(int v)      { this.reviewCount = v; }
    public double       getAverageRating()         { return averageRating; }
    public void         setAverageRating(double v) { this.averageRating = v; }
    public List<String> getPros()                  { return pros; }
    public void         setPros(List<String> v)    { this.pros = v; }
    public List<String> getCons()                  { return cons; }
    public void         setCons(List<String> v)    { this.cons = v; }
    public String       getSummary()               { return summary; }
    public void         setSummary(String v)       { this.summary = v; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════╗\n");
        sb.append(String.format("  %s%n", productName));
        sb.append(String.format("  Проанализировано отзывов: %d | Средняя оценка: %.1f★%n",
                reviewCount, averageRating));
        sb.append("──────────────────────────────────────────────────────\n");
        sb.append("  ✅ ПЛЮСЫ:\n");
        if (pros != null) pros.forEach(p -> sb.append("     • ").append(p).append("\n"));
        sb.append("  ❌ МИНУСЫ:\n");
        if (cons != null) cons.forEach(c -> sb.append("     • ").append(c).append("\n"));
        sb.append("  📝 Вывод: ").append(summary).append("\n");
        sb.append("╚══════════════════════════════════════════════════════╝\n");
        return sb.toString();
    }
}

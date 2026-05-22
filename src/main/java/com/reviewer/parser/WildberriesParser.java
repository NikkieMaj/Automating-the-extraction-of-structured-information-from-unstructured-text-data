package com.reviewer.parser;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.reviewer.model.Review;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


public class WildberriesParser {

    private static final String REVIEWS_URL =
            "https://www.wildberries.ru/catalog/%s/feedbacks";
    private static final String PROFILE_DIR = "C:\\temp\\wb-profile";

    public List<Review> parseReviews(String productId, int maxReviews) throws Exception {

        List<Review> reviews = new ArrayList<>();
        new java.io.File(PROFILE_DIR).mkdirs();

        System.out.println("[WB] Запускаем браузер...");
        System.out.println("[WB] Профиль сохраняется в: " + PROFILE_DIR);

        try (Playwright playwright = Playwright.create()) {
            BrowserContext context = playwright.chromium().launchPersistentContext(
                Paths.get(PROFILE_DIR),
                new BrowserType.LaunchPersistentContextOptions()
                    .setHeadless(false)
                    .setSlowMo(150)
                    .setViewportSize(1280, 800)
                    .setLocale("ru-RU")
                    .setTimezoneId("Europe/Moscow")
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                  "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                  "Chrome/124.0.0.0 Safari/537.36")
                    .setArgs(List.of(
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--no-first-run",
                        "--no-default-browser-check"
                    ))
            );
            context.addInitScript("""
                Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
                window.chrome = { runtime: {} };
            """);

            Page page = context.newPage();
            String url = String.format(REVIEWS_URL, productId);
            System.out.println("[WB] Открываем: " + url);
            page.navigate(url);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForTimeout(3000);
            int capAttempts = 0;
            while (isBlocked(page) && capAttempts < 3) {
                System.out.println("[WB] Блокировка WB, ждём 65 секунд...");
                page.waitForTimeout(65_000);
                page.reload();
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                page.waitForTimeout(3000);
                capAttempts++;
            }

            if (isBlocked(page)) {
                throw new RuntimeException(
                    "WB заблокировал браузер. Попробуйте открыть wildberries.ru вручную, " +
                    "пройти проверку, и запустить программу снова.");
            }

            String productName = getProductName(page, productId);
            System.out.println("[WB] Товар: «" + productName + "»");

            try {
                page.waitForSelector(
                    ".comments__item, .feedback-card, [class*='feedbackCard'], [class*='feedback-card']",
                    new Page.WaitForSelectorOptions().setTimeout(15_000));
            } catch (Exception e) {
                System.out.println("[WB] Отзывы не появились за 15 сек, читаем что есть...");
            }

            int prevCount = 0, noChange = 0;

            while (reviews.size() < maxReviews && noChange < 3) {
                List<ElementHandle> cards = findReviewCards(page);

                for (int i = prevCount; i < cards.size() && reviews.size() < maxReviews; i++) {
                    Review r = extractReview(cards.get(i), productName, productId);
                    if (r != null) reviews.add(r);
                }

                System.out.printf("[WB] Собрано: %d/%d отзывов%n", reviews.size(), maxReviews);
                if (reviews.size() >= maxReviews) break;

                page.evaluate("window.scrollBy({ top: 800, behavior: 'smooth' })");
                page.waitForTimeout(1000 + (long)(Math.random() * 800));

                boolean clicked = clickShowMore(page);
                if (cards.size() == prevCount) noChange++;
                else { noChange = 0; prevCount = cards.size(); }

                if (!clicked && cards.size() == prevCount) break;
                page.waitForTimeout(2000);
            }

            context.close();
        }

        System.out.printf("[WB] Итого: %d отзывов%n", reviews.size());
        return reviews;
    }

    private boolean isBlocked(Page page) {
        try {
            String content = page.content();
            return content.contains("Подозрительная активность")
                || content.contains("captcha")
                || page.url().contains("capcha");
        } catch (Exception e) {
            return false;
        }
    }

    private String getProductName(Page page, String fallback) {
        try {
            String title = page.title().trim();
            if (!title.isEmpty() && !title.equals("Wildberries")) {
                title = title.replaceAll("\s*[—–-]+\s*Wildberries.*$", "").trim();
                title = title.replaceAll("^Отзывы на\s+", "");
                title = title.replaceAll("\s+в интернет.магазине.*$", "");
                title = title.replaceAll("\s{6,}$", "").trim();
                if (!title.isEmpty() && title.length() > 3) return title;
            }
        } catch (Exception ignored) {}

        try {
            String meta = page.locator("meta[property='og:title']").getAttribute("content");
            if (meta != null && !meta.isBlank()) {
                meta = meta.replaceAll("\s*[—–-]+\s*Wildberries.*$", "").trim();
                if (!meta.isEmpty()) return meta;
            }
        } catch (Exception ignored) {}

        for (String sel : new String[]{
                "h1.product-page__title", ".product-page__title",
                "h1[class*='title']", "h1"}) {
            try {
                Locator el = page.locator(sel).first();
                if (el.isVisible()) {
                    String t = el.textContent().trim();
                    if (!t.isEmpty() && t.length() > 3) return t;
                }
            } catch (Exception ignored) {}
        }

        return "Товар " + fallback;
    }

    private List<ElementHandle> findReviewCards(Page page) {
        for (String sel : new String[]{
                ".comments__item", ".feedback-card",
                "[class*='feedbackCard']", "[class*='feedback-card']",
                "li[class*='comments']"}) {
            try {
                List<ElementHandle> cards = page.querySelectorAll(sel);
                if (!cards.isEmpty()) return cards;
            } catch (Exception ignored) {}
        }
        return List.of();
    }

    private Review extractReview(ElementHandle card, String productName, String productId) {
        try {
            String text = extractText(card,
                ".feedback__text", "[class*='feedbackText']",
                "[class*='feedback__text']", "p[class*='text']", "p");
            if (text.isEmpty()) return null;

            String author = extractText(card,
                ".feedback__header-author", "[class*='userName']",
                "[class*='authorName']", "[class*='feedback__name']");
            if (author.isEmpty()) author = "Аноним";

            int    rating = extractRating(card);
            String date   = extractText(card,
                ".feedback__date", "[class*='feedbackDate']", "time", "[class*='date']");

            return new Review("Wildberries", productName, productId, author, rating, text, date);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractText(ElementHandle card, String... selectors) {
        for (String sel : selectors) {
            try {
                ElementHandle el = card.querySelector(sel);
                if (el != null) {
                    String t = el.textContent().trim();
                    if (!t.isEmpty()) return t;
                }
            } catch (Exception ignored) {}
        }
        return "";
    }

    private int extractRating(ElementHandle card) {
        for (String sel : new String[]{"[class*='stars']", "[class*='rating']", "[class*='star']"}) {
            try {
                ElementHandle el = card.querySelector(sel);
                if (el != null) {
                    String aria = el.getAttribute("aria-label");
                    if (aria != null && aria.matches(".*\\d.*"))
                        return Integer.parseInt(aria.replaceAll("[^0-9].*", "").substring(0, 1));
                    String dr = el.getAttribute("data-rating");
                    if (dr != null) return (int) Double.parseDouble(dr);
                }
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private boolean clickShowMore(Page page) {
        for (String sel : new String[]{
                ".comments__btn", "[class*='showMore']", "[class*='show-more']",
                "button[class*='more']", ".pagination__btn--next"}) {
            try {
                Locator btn = page.locator(sel).first();
                if (btn.isVisible()) { btn.click(); page.waitForTimeout(2500); return true; }
            } catch (Exception ignored) {}
        }
        return false;
    }
}

package com.reviewer.bot;

import com.google.gson.*;
import com.reviewer.Main;
import com.reviewer.model.AnalysisResult;
import com.reviewer.model.Review;
import com.reviewer.parser.WildberriesParser;
import com.reviewer.service.AiService;
import com.reviewer.web.ProfileManager;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

public class ReviewBot implements Runnable {

    private static final String API = "https://api.telegram.org/bot";

    private final String token;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private long lastUpdateId = 0;
    private volatile boolean running = true;

    public ReviewBot(String token) {this.token = token;}

    public void start() {
        Thread t = new Thread(this, "telegram-bot");
        t.setDaemon(true);
        t.start();
        System.out.println("Telegram бот запущен");
    }

    @Override
    public void run() {
        while (running) {
            try {
                String url = API + token + "/getUpdates?timeout=30&offset=" + (lastUpdateId + 1);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url)).timeout(Duration.ofSeconds(40)).GET().build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

                JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (!body.get("ok").getAsBoolean()) {
                    System.err.println("[BOT] getUpdates ошибка: " + body);
                    Thread.sleep(3000);
                    continue;
                }

                JsonArray updates = body.getAsJsonArray("result");
                for (JsonElement el : updates) {
                    JsonObject update = el.getAsJsonObject();
                    lastUpdateId = update.get("update_id").getAsLong();
                    handleUpdate(update);
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("[BOT] Polling: " + e.getMessage());
                    try {Thread.sleep(3000); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    private void handleUpdate(JsonObject update) {
        if (!update.has("message")) return;
        JsonObject message = update.getAsJsonObject("message");
        if (!message.has("text")) return;

        String text = message.get("text").getAsString().trim();
        long chatId = message.getAsJsonObject("chat").get("id").getAsLong();
        String firstName = message.getAsJsonObject("from").get("first_name").getAsString();

        System.out.println("[BOT] " + firstName + ": " + text);

        JsonObject user = ProfileManager.get().getOrCreateUser(chatId, firstName);

        if (text.startsWith("/start")) {
            sendMd(chatId,
                    "Привет, " + esc(firstName) + "!\n\n"
                            + "Я анализирую отзывы с Wildberries через ИИ\\.\n\n"
                            + "Отправь артикул товара — число из URL\\.\n\n"
                            + "*Команды:*\n"
                            + "/code — код для входа на сайт\n"
                            + "/profiles — мои профили\n"
                            + "/addprofile Имя — добавить профиль\n"
                            + "/help — помощь");
            return;
        }

        if (text.startsWith("/code")) {
            String code = ProfileManager.get().refreshCode(chatId);
            sendMd(chatId,
                    "Твой код для входа на сайт:\n\n"
                            + "`" + code + "`\n\n"
                            + "Введи этот код на сайте `localhost:8080` чтобы связать аккаунт\\.\n"
                            + "_Код действует до следующего /code_");
            return;
        }

        if (text.startsWith("/profiles")) {
            JsonArray profiles = ProfileManager.get().getProfiles(chatId);
            String activeId = user.get("activeProfileId").getAsString();
            StringBuilder sb = new StringBuilder("Твои профили:\n\n");
            int i = 1;
            for (JsonElement el : profiles) {
                JsonObject p = el.getAsJsonObject();
                boolean active = p.get("id").getAsString().equals(activeId);
                sb.append(active ? "▶️ " : "   ").append(i++).append("\\. ")
                        .append(esc(p.get("name").getAsString()))
                        .append(active ? " _(активный)_" : "").append("\n");
            }
            sb.append("\nЧтобы переключить: `/switch 2`");
            sendMd(chatId, sb.toString());
            return;
        }

        if (text.startsWith("/addprofile ")) {
            String name = text.substring(12).trim();
            if (name.isEmpty()) { sendMd(chatId, "⚠️ Укажи имя: `/addprofile Мама`"); return; }
            ProfileManager.get().addProfile(chatId, name);
            sendMd(chatId, "✅ Профиль *" + esc(name) + "* создан!");
            return;
        }

        if (text.startsWith("/switch ")) {
            try {
                int idx = Integer.parseInt(text.substring(8).trim()) - 1;
                JsonArray profiles = ProfileManager.get().getProfiles(chatId);
                if (idx < 0 || idx >= profiles.size()) {
                    sendMd(chatId, "⚠️ Неверный номер профиля\\. Используй /profiles"); return;
                }
                JsonObject p = profiles.get(idx).getAsJsonObject();
                ProfileManager.get().switchProfile(chatId, p.get("id").getAsString());
                sendMd(chatId, "✅ Переключено на профиль *" + esc(p.get("name").getAsString()) + "*");
            } catch (Exception e) {
                sendMd(chatId, "⚠️ Используй: `/switch 2`");
            }
            return;
        }

        if (text.startsWith("/help")) {
            sendMd(chatId,
                    "ℹ️ *Команды:*\n\n"
                            + "`/code` — получить код для сайта\n"
                            + "`/profiles` — список профилей\n"
                            + "`/addprofile Имя` — создать профиль\n"
                            + "`/switch N` — переключить профиль\n\n"
                            + "Просто отправь артикул товара чтобы проанализировать отзывы\\.");
            return;
        }

        // Артикул товара
        if (!text.matches("\\d+")) {
            sendMd(chatId, "⚠️ Артикул должен состоять из цифр\\.\n\nПример: `367514477`");
            return;
        }

        String productId = text;
        String activeId  = user.get("activeProfileId").getAsString();
        JsonObject activeProfile = ProfileManager.get().getActiveProfile(chatId);
        String profileName = activeProfile != null
                ? activeProfile.get("name").getAsString() : "Мои запросы";

        sendMd(chatId, "⏳ Ищу отзывы для *" + productId + "*\\.\\.\\.\n"
                + "Профиль: _" + esc(profileName) + "_\n"
                + "Открываю браузер, подождите 1–2 минуты\\.");

        executor.submit(() -> {
            try {
                List<Review> reviews =
                        new WildberriesParser().parseReviews(productId, Main.MAX_REVIEWS);
                if (reviews.isEmpty()) {
                    sendMd(chatId, "❌ Отзывы не найдены\\. Проверьте артикул\\."); return;
                }
                sendMd(chatId, "🤖 Загружено *" + reviews.size() + "* отзывов\\. Анализирую\\.");

                AnalysisResult result = new AiService(Main.GROQ_API_KEY, "").analyze(reviews);
                ProfileManager.get().saveAnalysis(chatId, activeId, productId, result);

                sendMd(chatId, formatResult(result, profileName));
            } catch (Exception e) {
                System.err.println("[BOT] Ошибка: " + e.getMessage());
                sendPlain(chatId, "❌ Ошибка: " + e.getMessage());
            }
        });
    }

    private String formatResult(AnalysisResult r, String profileName) {
        StringBuilder sb = new StringBuilder();
        sb.append("🛍 *").append(esc(r.getProductName())).append("*\n");
        sb.append("📊 Отзывов: ").append(r.getReviewCount())
                .append(" · Профиль: _").append(esc(profileName)).append("_\n\n");
        sb.append("✅ *Плюсы:*\n");
        r.getPros().forEach(p -> sb.append("• ").append(esc(p)).append("\n"));
        sb.append("\n❌ *Минусы:*\n");
        r.getCons().forEach(c -> sb.append("• ").append(esc(c)).append("\n"));
        sb.append("\n📝 *Вывод:* ").append(esc(r.getSummary()));
        sb.append("\n\n🌐 История на сайте: `localhost:8080`");
        return sb.toString();
    }

    /** Экранирование всех спецсимволов MarkdownV2 */
    private String esc(String t) {
        if (t == null) return "";
        return t.replace("\\","\\\\").replace("_","\\_").replace("*","\\*")
                .replace("[","\\[").replace("]","\\]").replace("(","\\(")
                .replace(")","\\)").replace("~","\\~").replace("`","\\`")
                .replace(">","\\>").replace("#","\\#").replace("+","\\+")
                .replace("-","\\-").replace("=","\\=").replace("|","\\|")
                .replace("{","\\{").replace("}","\\}").replace(".","\\.")
                .replace("!","\\!");
    }

    /** Отправка с Markdown, с fallback на plain text при любой ошибке */
    private void sendMd(long chatId, String text) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("chat_id",    chatId);
            body.addProperty("text",       text);
            body.addProperty("parse_mode", "MarkdownV2");
            post("/sendMessage", body.toString());
        } catch (Exception e) {
            System.err.println("[BOT] Ошибка MarkdownV2, отправляю без форматирования: " + e.getMessage());
            sendPlain(chatId, text);
        }
    }

    private void sendPlain(long chatId, String text) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("chat_id", chatId);
            body.addProperty("text",    text);
            post("/sendMessage", body.toString());
        } catch (Exception e) {
            System.err.println("[BOT] Не удалось отправить plain сообщение: " + e.getMessage());
        }
    }

    private void post(String method, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API + token + method))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        JsonObject responseJson = JsonParser.parseString(resp.body()).getAsJsonObject();
        if (!responseJson.get("ok").getAsBoolean()) {
            String description = responseJson.has("description")
                    ? responseJson.get("description").getAsString()
                    : "неизвестная ошибка";
            throw new RuntimeException("Telegram API error: " + description);
        }
        System.out.println("[BOT] → " + resp.body().substring(0, Math.min(60, resp.body().length())));
    }
}
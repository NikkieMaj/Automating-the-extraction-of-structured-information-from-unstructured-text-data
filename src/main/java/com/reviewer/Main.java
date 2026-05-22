package com.reviewer;

import com.reviewer.bot.ReviewBot;
import com.reviewer.web.WebServer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {

    public static final String GROQ_API_KEY;
    public static final String TELEGRAM_BOT_TOKEN;
    public static final int MAX_REVIEWS;
    public static final int SERVER_PORT;

    static {
        loadEnv(".env");
        GROQ_API_KEY = getEnv("GROQ_API_KEY",        "");
        TELEGRAM_BOT_TOKEN = getEnv("TELEGRAM_BOT_TOKEN",  "");
        MAX_REVIEWS = Integer.parseInt(getEnv("MAX_REVIEWS",  "30"));
        SERVER_PORT = Integer.parseInt(getEnv("SERVER_PORT",  "8080"));
    }

    private static void loadEnv(String filename) {
        if (!Files.exists(Paths.get(filename))) return;
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 1) continue;
                String key   = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (System.getenv(key) == null) {
                    System.setProperty("env." + key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("Не удалось прочитать .env: " + e.getMessage());
        }
    }

    private static String getEnv(String key, String defaultValue) {
        String val = System.getenv(key);
        if (val != null && !val.isEmpty()) return val;
        val = System.getProperty("env." + key);
        if (val != null && !val.isEmpty()) return val;
        return defaultValue;
    }

    public static void main(String[] args) throws Exception {
        if (GROQ_API_KEY.isEmpty()) {
            System.err.println("Ошибка: GROQ_API_KEY не задан. Укажите его в файле .env");
            System.exit(1);
        }

        WebServer server = new WebServer(SERVER_PORT);
        server.start();
        System.out.println("Анализатор отзывов Wildberries + Groq AI");
        System.out.println();
        System.out.println("Веб-интерфейс: http://localhost:" + SERVER_PORT);

        if (!TELEGRAM_BOT_TOKEN.isEmpty()) {
            new ReviewBot(TELEGRAM_BOT_TOKEN).start();
        } else {
            System.out.println("Telegram бот не настроен — укажите TELEGRAM_BOT_TOKEN в .env");
        }

        System.out.println("Для остановки нажмите Ctrl+C");
        System.out.println();

        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI("http://localhost:" + SERVER_PORT));
        } catch (Exception e) {
            System.out.println("Откройте вручную: http://localhost:" + SERVER_PORT);
        }
    }
}

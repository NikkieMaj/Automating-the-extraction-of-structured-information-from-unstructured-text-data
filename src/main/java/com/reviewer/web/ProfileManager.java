package com.reviewer.web;

import com.google.gson.*;
import com.reviewer.model.AnalysisResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class ProfileManager {

    private static final String FILE = "users.json";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ProfileManager instance;
    private JsonArray users;

    private ProfileManager() { users = loadFromDisk(); }

    public static synchronized ProfileManager get() {
        if (instance == null) instance = new ProfileManager();
        return instance;
    }


    public synchronized JsonObject getOrCreateUser(long telegramId, String firstName) {
        for (JsonElement el : users) {
            JsonObject u = el.getAsJsonObject();
            if (u.get("telegramId").getAsLong() == telegramId) return u;
        }
        JsonObject user = new JsonObject();
        user.addProperty("telegramId", telegramId);
        user.addProperty("firstName", firstName);
        user.addProperty("webCode", generateCode());
        user.addProperty("activeProfileId","default");

        JsonArray profiles = new JsonArray();
        profiles.add(makeProfile("default", "Мои запросы"));
        user.add("profiles", profiles);

        users.add(user);
        save();
        return user;
    }

    public synchronized JsonObject getUserByCode(String code) {
        for (JsonElement el : users) {
            JsonObject u = el.getAsJsonObject();
            if (u.get("webCode").getAsString().equals(code)) return u;
        }
        return null;
    }

    public synchronized JsonObject getUserByTelegramId(long telegramId) {
        for (JsonElement el : users) {
            JsonObject u = el.getAsJsonObject();
            if (u.get("telegramId").getAsLong() == telegramId) return u;
        }
        return null;
    }

    public synchronized String refreshCode(long telegramId) {
        JsonObject user = getUserByTelegramId(telegramId);
        if (user == null) return null;
        String code = generateCode();
        user.addProperty("webCode", code);
        save();
        return code;
    }


    public synchronized boolean addProfile(long telegramId, String name) {
        JsonObject user = getUserByTelegramId(telegramId);
        if (user == null) return false;
        String id = "p" + System.currentTimeMillis();
        user.getAsJsonArray("profiles").add(makeProfile(id, name));
        save();
        return true;
    }

    public synchronized boolean deleteProfile(long telegramId, String profileId) {
        if ("default".equals(profileId)) return false;
        JsonObject user = getUserByTelegramId(telegramId);
        if (user == null) return false;

        JsonArray profiles = user.getAsJsonArray("profiles");
        JsonArray updated  = new JsonArray();
        for (JsonElement el : profiles)
            if (!el.getAsJsonObject().get("id").getAsString().equals(profileId))
                updated.add(el);

        if (updated.size() == profiles.size()) return false; // не найден
        user.add("profiles", updated);

        if (user.get("activeProfileId").getAsString().equals(profileId))
            user.addProperty("activeProfileId", "default");

        save();
        return true;
    }

    public synchronized boolean switchProfile(long telegramId, String profileId) {
        JsonObject user = getUserByTelegramId(telegramId);
        if (user == null) return false;
        user.addProperty("activeProfileId", profileId);
        save();
        return true;
    }

    public synchronized JsonObject getActiveProfile(long telegramId) {
        JsonObject user = getUserByTelegramId(telegramId);
        if (user == null) return null;
        String activeId = user.get("activeProfileId").getAsString();
        return findProfile(user, activeId);
    }

    public synchronized JsonArray getProfiles(long telegramId) {
        JsonObject user = getUserByTelegramId(telegramId);
        if (user == null) return new JsonArray();
        return user.getAsJsonArray("profiles");
    }


    public synchronized void saveAnalysis(long telegramId, String profileId,
                                          String productId, AnalysisResult result) {
        JsonObject user = getUserByTelegramId(telegramId);
        if (user == null) {
            System.out.println("[PM] Пользователь " + telegramId + " не найден — создаём");
            user = getOrCreateUser(telegramId, "User" + telegramId);
        }

        JsonObject profile = findProfile(user, profileId);
        if (profile == null) {
            System.out.println("[PM] Профиль " + profileId + " не найден, поэтому используем default");
            profile = findProfile(user, "default");
        }
        if (profile == null) {
            System.out.println("[PM] Ошибка: профиль default не найден для " + telegramId);
            return;
        }
        System.out.println("[PM] Сохраняем анализ для user=" + telegramId + " profile=" + profile.get("name").getAsString());

        JsonObject entry = new JsonObject();
        entry.addProperty("date", LocalDateTime.now().format(FMT));
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

        JsonArray analyses = profile.getAsJsonArray("analyses");
        JsonArray updated = new JsonArray();
        updated.add(entry);
        analyses.forEach(updated::add);
        profile.add("analyses", updated);

        save();
    }

    public synchronized JsonObject getHistoryPage(long telegramId, String profileId, int page, int pageSize) {
        JsonObject user = getUserByTelegramId(telegramId);
        JsonObject result = new JsonObject();
        if (user == null) { result.add("items", new JsonArray()); result.addProperty("total", 0); return result;}

        JsonObject profile = findProfile(user, profileId);
        if (profile == null) { result.add("items", new JsonArray()); result.addProperty("total", 0); return result;}

        JsonArray all = profile.getAsJsonArray("analyses");
        int total = all.size();
        int from = page * pageSize;
        int to = Math.min(from + pageSize, total);

        JsonArray page_items = new JsonArray();
        for (int i = from; i < to; i++) page_items.add(all.get(i));

        result.add("items", page_items);
        result.addProperty("total", total);
        result.addProperty("page", page);
        result.addProperty("pages", (int) Math.ceil((double) total / pageSize));
        return result;
    }

    private JsonObject makeProfile(String id, String name) {
        JsonObject p = new JsonObject();
        p.addProperty("id",   id);
        p.addProperty("name", name);
        p.add("analyses", new JsonArray());
        return p;
    }

    private JsonObject findProfile(JsonObject user, String profileId) {
        for (JsonElement el : user.getAsJsonArray("profiles")) {
            JsonObject p = el.getAsJsonObject();
            if (p.get("id").getAsString().equals(profileId)) return p;
        }
        return null;
    }

    private String generateCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    private synchronized void save() {
        try { Files.writeString(Path.of(FILE), GSON.toJson(users), StandardCharsets.UTF_8); }
        catch (Exception e) { System.err.println("[PM] Не удалось сохранить: " + e.getMessage()); }
    }

    private JsonArray loadFromDisk() {
        try {
            if (!Files.exists(Path.of(FILE))) return new JsonArray();
            return JsonParser.parseString(Files.readString(Path.of(FILE))).getAsJsonArray();
        } catch (Exception e) { return new JsonArray(); }
    }
}

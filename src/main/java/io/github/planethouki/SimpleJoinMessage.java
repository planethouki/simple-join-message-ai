package io.github.planethouki;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

public final class SimpleJoinMessage extends JavaPlugin implements Listener {

    private String openAiApiKey;
    private String defaultMessage;
    private String systemPrompt;
    private String userPrompt;
    private int maxTokens;

    private final HashMap<UUID, Long> lastGreetTimes = new HashMap<>();
    private final long cooldownTime = 5 * 60 * 1000;

    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("onEnable is called!");

        // config.ymlが存在しない場合に作成
        saveDefaultConfig();

        loadConfigValues();

        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        long currentTime = System.currentTimeMillis();
        if (lastGreetTimes.containsKey(playerId) && (currentTime - lastGreetTimes.get(playerId) < cooldownTime)) {
            return;
        }

        lastGreetTimes.put(playerId, currentTime);

        if (openAiApiKey == null || openAiApiKey.isEmpty() ) {
            player.sendMessage(ChatColor.AQUA + "[Server] " + ChatColor.RESET + defaultMessage);
        }

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            String greeting = fetchGreetingFromOpenAi();

            getServer().getScheduler().runTask(this, () -> {
                if (greeting != null) {
                    player.sendMessage(ChatColor.AQUA + "[Server] " + ChatColor.RESET + greeting);
                }
            });
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // コマンドが "sjm" の場合
        if (command.getName().equalsIgnoreCase("sjm")) {
            // サブコマンドが "reload" の場合
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                loadConfigValues();
                sender.sendMessage("Config reloaded!");
                return true;
            } else {
                sender.sendMessage("Usage: /sjm reload");
                return false;
            }
        }
        return false;
    }

    private void loadConfigValues() {
        // 設定ファイルの再ロード
        reloadConfig();

        FileConfiguration config = getConfig();

        // 必要な設定値を取得
        openAiApiKey = config.getString("openai-api-key");
        defaultMessage = config.getString("welcome-message", "Welcome to the server!");
        systemPrompt = config.getString("system-prompt", "You are a helpful assistant.");
        userPrompt = config.getString("user-prompt", "Think of a one-line welcome message for the user and return only the message.");
        maxTokens = config.getInt("max-tokens", 50);

        // 設定が正しいか確認
        if (openAiApiKey == null || openAiApiKey.isEmpty()) {
            getLogger().severe("OpenAI API key is not set in config.yml!");
        }
    }

    private String fetchGreetingFromOpenAi() {
        OkHttpClient client = new OkHttpClient();
        String apiUrl = "https://api.openai.com/v1/chat/completions";

        JSONObject jsonRequest = new JSONObject();
        jsonRequest.put("model", "gpt-4o-mini");
        jsonRequest.put("max_tokens", maxTokens);
        jsonRequest.put("messages", new JSONObject[]{
                new JSONObject().put("role", "system").put("content", systemPrompt),
                new JSONObject().put("role", "user").put("content", userPrompt)
        });

        RequestBody body = RequestBody.create(jsonRequest.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer " + openAiApiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                // Extract the greeting from the response (basic JSON parsing)
                return extractGreetingFromResponse(responseBody);
            } else {
                getLogger().warning("Failed to fetch greeting: " + response.code() + " " + response.message());
                return null;
            }
        } catch (IOException e) {
            getLogger().severe("Error while contacting OpenAI API: " + e.getMessage());
            return null;
        }
    }

    private String extractGreetingFromResponse(String response) {
        JSONObject jsonObject = new JSONObject(response);
        JSONArray choices = jsonObject.getJSONArray("choices");
        String content = choices.getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
        return content;
    }
}

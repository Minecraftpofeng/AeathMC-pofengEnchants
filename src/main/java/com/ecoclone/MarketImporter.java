package com.ecoclone;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * v1.4.7 高级功能：市场（LRCDB / EcoEnchants 社区库）附魔配置导入。
 *
 * 用法（/aeath import）：
 *   /aeath import <key>                 按 config.yml 的 market.source 模板下载（%s 替换为 key）
 *   /aeath import <key> <直链>          从指定直链下载并保存为 <key>.yml
 *   /aeath import --force <key> [...]   覆盖同名配置
 *
 * 导入后的 yml 落在插件目录 enchantments/ 下：
 *   - 静态清单里已有的 key：立即热生效（/aeath reload 同效）；
 *   - 新 key：写进 bootstrap 注册清单，下次重启后注册为真实附魔。
 */
public final class MarketImporter {

    private final EcoClonePlugin plugin;
    private final EnchantManager manager;

    public MarketImporter(EcoClonePlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.manager();
    }

    /** 执行导入；返回给玩家看的结果文本。失败返回以 §c 开头的消息。 */
    public String importFrom(String keyOrUrl, String urlOverride, boolean force) {
        String key;
        String url;
        if (urlOverride != null && !urlOverride.isEmpty()) {
            key = sanitizeKey(keyOrUrl);
            url = urlOverride.trim();
        } else if (keyOrUrl.startsWith("http://") || keyOrUrl.startsWith("https://")) {
            url = keyOrUrl.trim();
            key = sanitizeKey(guessKey(url));
        } else {
            key = sanitizeKey(keyOrUrl);
            url = String.format(manager.marketSource(), key);
        }
        if (key == null || key.isEmpty()) {
            return "§c无法确定附魔 key，请使用 /aeath import <key> 或 /aeath import <key> <直链>";
        }
        try {
            byte[] data = download(url);
            if (data == null || data.length == 0) {
                return "§c下载失败：返回内容为空（" + url + "）";
            }
            String text = new String(data, StandardCharsets.UTF_8);
            if (!text.contains("name:") || !text.contains("general-config")) {
                return "§c下载内容不是有效的附魔配置（需要包含 name 与 general-config），来源：" + url;
            }
            File dir = new File(plugin.getDataFolder(), "enchantments");
            if (!dir.exists() && !dir.mkdirs()) {
                return "§c无法创建 enchantments 目录：" + dir.getAbsolutePath();
            }
            File target = new File(dir, key + ".yml");
            boolean existed = target.exists();
            if (existed && !force && !manager.marketOverwrite()) {
                return "§c已存在同名配置 " + target.getName() + "，加 --force 覆盖，或在 config.yml 打开 market.overwrite";
            }
            boolean isKnown = Enchants.definitions().stream().anyMatch(d -> d.key.equals(key));
            Files.write(target.toPath(), data);
            record(key, url);
            plugin.getServer().getScheduler().runTask(plugin, plugin::reload);
            return "§a导入成功 §f" + key + " §7→ §f" + target.getAbsolutePath()
                    + (isKnown ? " §7（已热加载生效）" : " §7（新附魔，重启服务端后注册生效）");
        } catch (Throwable t) {
            return "§c导入失败：" + t.getMessage();
        }
    }

    private byte[] download(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "AeathMC-pofengEnchants/v" + plugin.getDescription().getVersion());
        c.setRequestProperty("Accept", "text/yaml, text/plain, */*");
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new IllegalStateException("HTTP " + code + " " + url);
        }
        byte[] out;
        try (var in = c.getInputStream()) {
            out = in.readAllBytes();
        }
        c.disconnect();
        return out;
    }

    private void record(String key, String url) {
        try {
            File f = new File(plugin.getDataFolder(), "market-imported.txt");
            String line = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    + "  " + key + "  " + url + System.lineSeparator();
            Files.write(f.toPath(), line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    private static String sanitizeKey(String s) {
        if (s == null) {
            return null;
        }
        String k = s.toLowerCase().replaceAll("[^a-z0-9_\\-]", "");
        return k.isEmpty() ? null : k;
    }

    private static String guessKey(String url) {
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        if (name.endsWith(".yml")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }
}

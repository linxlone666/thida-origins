package dev.thida;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;

/** Dedicated servers may not load client language packs; retain Thai fallback text. */
public final class ThidaMessages {
  private static final Map<String, String> THAI = load();

  private ThidaMessages() {}

  public static String text(String key) {
    return Component.translatableWithFallback(key, THAI.getOrDefault(key, key)).getString();
  }

  private static Map<String, String> load() {
    var result = new HashMap<String, String>();
    try (var stream = ThidaMessages.class.getResourceAsStream("/assets/thida/lang/th_th.json")) {
      if (stream != null)
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
          JsonParser.parseReader(reader)
              .getAsJsonObject()
              .entrySet()
              .forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsString()));
        }
    } catch (Exception error) {
      com.mojang.logging.LogUtils.getLogger()
          .warn("Thida language fallback unavailable: {}", error.toString());
    }
    return Map.copyOf(result);
  }
}

package dev.thida;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ThaiLanguageTest {
  @Test
  void thaiIsAlsoTheFallbackForEveryMinecraftLanguage() throws Exception {
    try (var thai = getClass().getResourceAsStream("/assets/thida/lang/th_th.json");
        var fallback = getClass().getResourceAsStream("/assets/thida/lang/en_us.json")) {
      assertNotNull(thai);
      assertNotNull(fallback);
      var expected =
          JsonParser.parseReader(new InputStreamReader(thai, StandardCharsets.UTF_8))
              .getAsJsonObject();
      var actual =
          JsonParser.parseReader(new InputStreamReader(fallback, StandardCharsets.UTF_8))
              .getAsJsonObject();
      assertEquals(expected, actual);
      for (var entry : actual.entrySet())
        assertTrue(entry.getValue().getAsString().matches("(?s).*[ก-๛].*"), entry.getKey());
      assertEquals("ตุ๊กตานรตรา", actual.get("item.thida.narotra_doll").getAsString());
      assertEquals("ตุ๊กตานิลกาล", actual.get("item.thida.nilakan_doll").getAsString());
      assertEquals("ตุ๊กตากาฝาก", actual.get("item.thida.parasite_doll").getAsString());
    }
  }
}

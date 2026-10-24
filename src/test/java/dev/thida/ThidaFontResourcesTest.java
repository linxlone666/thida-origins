package dev.thida;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class ThidaFontResourcesTest {
  @Test
  void embeddedFontHasLicenseProvenanceAndUnmodifiedBytes() throws Exception {
    try (var font = getClass().getResourceAsStream("/assets/thida/font/itim-regular.ttf");
        var license = getClass().getResourceAsStream("/META-INF/licenses/Itim/OFL.txt");
        var source = getClass().getResourceAsStream("/META-INF/licenses/Itim/SOURCE.txt")) {
      assertNotNull(font);
      assertNotNull(license);
      assertNotNull(source);
      var bytes = font.readAllBytes();
      assertTrue(bytes.length > 10000);
      String terms = new String(license.readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(terms.contains("SIL OPEN FONT LICENSE Version 1.1"));
      assertTrue(terms.contains("Cadson Demak"));
      String provenance = new String(source.readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(
          provenance.contains(
              HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))));
    }
  }

  @Test
  void fontIsThidaScopedWithFallback() throws Exception {
    try (var resource = getClass().getResourceAsStream("/assets/thida/font/ui.json")) {
      assertNotNull(resource);
      var providers =
          JsonParser.parseString(new String(resource.readAllBytes(), StandardCharsets.UTF_8))
              .getAsJsonObject()
              .getAsJsonArray("providers");
      assertEquals(
          "thida:itim-regular.ttf", providers.get(0).getAsJsonObject().get("file").getAsString());
      assertEquals("minecraft:default", providers.get(1).getAsJsonObject().get("id").getAsString());
    }
  }
}

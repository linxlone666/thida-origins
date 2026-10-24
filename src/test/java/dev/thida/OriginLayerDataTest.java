package dev.thida;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class OriginLayerDataTest {
  @Test
  void selectionLayerUsesBoundOriginIdsForConnectorSingleplayer() throws Exception {
    try (var input = getClass().getResourceAsStream("/data/origins/origins/layer/origin.json")) {
      assertNotNull(input);
      var layer =
          JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
              .getAsJsonObject();
      var ids = new HashSet<String>();
      for (var value : layer.getAsJsonArray("origins")) {
        String id = value.getAsString();
        assertFalse(
            id.startsWith("#"), "Connector's client load must not receive a tag as an Origin ID");
        assertTrue(ids.add(id), "Duplicate Origin option: " + id);
      }
      assertEquals(11, ids.size(), "Keep all ten built-in choices and Thida");
      assertTrue(ids.contains("thida:thida"));
      assertTrue(ids.contains("origins:human"));
      assertTrue(ids.contains("origins:avian"));
    }
  }
}

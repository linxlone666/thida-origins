package dev.thida;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PixelSpritesTest {
    @Test void allMenuAndRelicSpritesHaveTransparentPixelBounds() {
        for(String key:new String[]{"nilakan","narotra","parasite","heart","domain","shield","cleanse","speed","weak","bleed","vulnerable","unlink","lock"}) {
            int[] sprite=PixelSprites.ALL.get(key);
            assertNotNull(sprite,key);
            assertEquals(256,sprite.length,key);
            int drawn=0,transparent=0;
            for(int pixel:sprite) {
                if(pixel==0) transparent++;
                else { drawn++; assertEquals(255,pixel>>>24,"No blurred/antialiased pixels"); }
            }
            assertTrue(drawn>10&&transparent>10,key);
        }
    }
    @Test void blessingAndCurseDollsHaveDistinctPalettes() {
        assertFalse(java.util.Arrays.equals(PixelSprites.ALL.get("nilakan"),PixelSprites.ALL.get("narotra")));
    }
}

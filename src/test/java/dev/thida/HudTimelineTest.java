package dev.thida;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudTimelineTest {
    @Test void activeThenRemainingCooldownThenHidden() {
        assertEquals(HudTimeline.Phase.ACTIVE, HudTimeline.phase(400, 600, false));
        assertEquals(400, HudTimeline.shownTicks(400, 600));
        assertEquals(HudTimeline.Phase.COOLDOWN, HudTimeline.phase(0, 200, false));
        assertEquals(200, HudTimeline.shownTicks(0, 200));
        assertEquals(HudTimeline.Phase.HIDDEN, HudTimeline.phase(0, 0, false));
    }
    @Test void instantAndSustainedEffectsHaveNoInventedDuration() {
        assertEquals(HudTimeline.Phase.COOLDOWN, HudTimeline.phase(0, 600, false));
        assertEquals(HudTimeline.Phase.ACTIVE, HudTimeline.phase(0, 0, true));
        assertEquals(HudTimeline.Phase.COOLDOWN, HudTimeline.phase(0, 12000, false));
    }
    @Test void longEffectStaysVisibleAfterCooldownExpires() {
        assertEquals(HudTimeline.Phase.ACTIVE, HudTimeline.phase(200, 0, false));
        assertEquals(200, HudTimeline.shownTicks(200, 0));
    }
}

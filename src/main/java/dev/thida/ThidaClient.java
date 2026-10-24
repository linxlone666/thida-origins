package dev.thida;

import java.util.List;
import java.util.ArrayList;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "thida", value = Dist.CLIENT)
public final class ThidaClient {
    public static final KeyMapping OPEN_MENU = key("open_doll_menu", GLFW.GLFW_KEY_1);
    public static final KeyMapping SUMMON = key("summon_parasite", GLFW.GLFW_KEY_2);
    public static final KeyMapping HEART = key("heart_bond", GLFW.GLFW_KEY_3);
    static { ThidaClientEffects.installConfirmation(ThidaClient::confirmation); }
    private static KeyMapping key(String id, int code) {
        return new KeyMapping("key.thida." + id, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, "key.categories.thida");
    }
    @EventBusSubscriber(modid = "thida", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class KeyRegistration {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent e) {
            e.register(OPEN_MENU); e.register(SUMMON); e.register(HEART);
        }
    }
    private static void request(String action, String doll, int spell) {
        var n = new CompoundTag(); n.putString("action", action); n.putString("doll", doll); n.putInt("spell", spell);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ThidaNetwork.SkillAction(n));
    }
    private static void drainConflicts(KeyMapping key) {
        var mc = Minecraft.getInstance();
        for (var other : mc.options.keyMappings) if (other != key && other.same(key)) {
            while (other.consumeClick()) {} other.setDown(false);
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        var mc = Minecraft.getInstance(); if (mc.player == null || mc.screen != null) return;
        if (!ThidaHudConfig.INPUT_V7.get()) {
            if (OPEN_MENU.getKey().getValue() == GLFW.GLFW_KEY_R) {
                OPEN_MENU.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_1));
                KeyMapping.resetMapping(); mc.options.save();
            }
            ThidaHudConfig.INPUT_V7.set(true); ThidaHudConfig.INPUT_V7.save();
        }
        while (OPEN_MENU.consumeClick()) { drainConflicts(OPEN_MENU); openMenu(); }
        while (SUMMON.consumeClick()) { drainConflicts(SUMMON); request("summon", "", 0); }
        while (HEART.consumeClick()) { drainConflicts(HEART); request("heart", "", 0); }
    }
    public static void openMenu() {
        var mc = Minecraft.getInstance(); if (mc.player == null) return;
        var held = new ArrayList<String>();
        for (String id : new String[]{"narotra_doll", "nilakan_doll"})
            if (mc.player.getMainHandItem().is(ThidaMod.ABILITIES.get(id).get())
                    || mc.player.getOffhandItem().is(ThidaMod.ABILITIES.get(id).get())) held.add(id);
        mc.setScreen(new WheelScreen(null, "วิชาแห่งธิดา", choices(held)));
    }
    private record Choice(String icon, String title, String reason, Runnable action) {}
    private static List<Choice> choices(List<String> held) {
        var rows = new ArrayList<Choice>();
        for (String kind : held) rows.add(new Choice(kind.startsWith("narotra") ? "narotra" : "nilakan",
                kind.startsWith("narotra") ? "นรตรา" : "นิลกาล", "", () -> spells(kind)));
        if (held.isEmpty()) rows.add(new Choice("narotra", "ถือหุ่นเพื่อเปิดรายการคาถา", "ยังไม่ได้ถือหุ่นนรตราหรือนิลกาล", () -> {}));
        rows.add(new Choice("unlink", "จัดตำแหน่ง HUD", "", () -> Minecraft.getInstance().setScreen(new ThidaBondHud.PositionScreen(Minecraft.getInstance().screen))));
        return rows;
    }
    private static void spells(String kind) {
        request("select", kind, 0);
        var mc = Minecraft.getInstance(); Screen parent = mc.screen;
        String[] names = kind.equals("narotra_doll") ? new String[]{"คุ้มครองจากดาเมจ", "ชำระและกันดีบัฟ", "ความเร็วและฟื้นพลังชีวิต"}
                : new String[]{"ลดดาเมจและความเร็วโจมตี", "เลือดไหลและตาบอด", "เพิ่มดาเมจที่เป้าหมายได้รับ"};
        String[] icons = kind.equals("narotra_doll") ? new String[]{"shield", "cleanse", "speed"} : new String[]{"weak", "bleed", "vulnerable"};
        var rows = new ArrayList<Choice>();
        for (int i = 0; i < 3; i++) {
            final int spell = i;
            rows.add(new Choice(icons[i], names[i], kind + "_" + i, () -> request("cast", kind, spell)));
        }
        rows.add(new Choice("unlink", "ยกเลิกสายสัมพันธ์", "unlink:" + kind, () -> {
            request("unlink", kind, 0); mc.setScreen(parent);
        }));
        mc.setScreen(new WheelScreen(parent, kind.equals("narotra_doll") ? "คาถานรตรา" : "คาถานิลกาล", rows));
    }
    private static void confirmation(CompoundTag data) {
        var mc = Minecraft.getInstance(); if (mc.player == null || !data.hasUUID("target")) return;
        boolean end = data.getString("kind").equals("heart_end");
        var rows = new ArrayList<Choice>(); Screen previous = mc.screen;
        rows.add(new Choice("heart", "ยืนยัน", "", () -> confirm(data, true, previous)));
        rows.add(new Choice("unlink", "ไม่ยืนยัน", "", () -> confirm(data, false, previous)));
        mc.setScreen(new WheelScreen(previous, (end ? "ยกเลิกพันธะกับ " : "เชื่อมกับ ") + data.getString("name") + "?", rows,
                () -> confirm(data, false, previous)));
    }
    private static void confirm(CompoundTag data, boolean accept, Screen previous) {
        var n = data.copy(); n.putString("action", "confirm"); n.putBoolean("accept", accept);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ThidaNetwork.SkillAction(n));
        Minecraft.getInstance().setScreen(previous);
    }
    private static final class WheelScreen extends Screen {
        private final Screen parent; private final List<Choice> rows; private int selected; private final Runnable cancel;
        WheelScreen(Screen parent, String title, List<Choice> rows) { this(parent, title, rows, null); }
        WheelScreen(Screen parent, String title, List<Choice> rows, Runnable cancel) { super(Component.literal(title)); this.parent = parent; this.rows = rows; this.cancel = cancel; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() { clearAttack(); if (cancel != null) cancel.run(); else minecraft.setScreen(parent); }
        private void clearAttack() {
            minecraft.options.keyAttack.setDown(false); while (minecraft.options.keyAttack.consumeClick()) {}
        }
        private String reason(Choice choice) {
            var data = ThidaHudState.snapshot();
            if (choice.reason().startsWith("unlink:")) return data.getCompound(choice.reason().substring(7)).getString("id").isEmpty() ? "ยังไม่มีสายสัมพันธ์" : "";
            if (choice.reason().startsWith("narotra_doll_") || choice.reason().startsWith("nilakan_doll_")) {
                if (!data.getBoolean("thida")) return "ต้องเลือกเผ่ากำเนิดธิดา";
                return data.getString("spellReason_" + choice.reason());
            }
            return choice.reason();
        }
        @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
            if (vertical != 0) selected = Math.floorMod(selected + (vertical < 0 ? 1 : -1), rows.size());
            return true;
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            clearAttack();
            if (button == 1) { onClose(); return true; }
            if (button == 0) {
                var row = rows.get(selected); String reason = reason(row);
                if (reason.isEmpty()) row.action().run();
                else if (minecraft.player != null) minecraft.player.displayClientMessage(Component.literal(reason), true);
            }
            return true;
        }
        @Override public boolean keyPressed(int key, int scan, int modifiers) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
            if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) return true;
            return super.keyPressed(key, scan, modifiers);
        }
        @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
            renderBackground(g, mouseX, mouseY, partial);
            int w = Math.min(380, width - 16), h = 51 + rows.size() * 35, x = (width - w) / 2, y = Math.max(8, (height - h) / 2);
            ThidaGui.frame(g, x, y, w, h, 0xFF91DAC2, true);
            ThidaGui.text(g, title.getString(), x + 10, y + 8, w - 20, ThidaGui.TEXT);
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i); String reason = reason(row); boolean active = i == selected;
                int ry = y + 25 + i * 35;
                if (active) ThidaGui.frame(g, x + 5, ry, w - 10, 32, 0xFF91DAC2, true);
                ThidaGui.icon(g, row.icon(), x + 11, ry + 6, 1);
                ThidaGui.text(g, (active ? "› " : "  ") + row.title(), x + 34, ry + 5, w - 48, reason.isEmpty() ? ThidaGui.TEXT : ThidaGui.MUTED);
                String status = reason.isEmpty() ? "คลิกซ้ายที่ใดก็ได้เพื่อเลือก" : reason;
                ThidaGui.text(g, status, x + 43, ry + 19, w - 57, reason.isEmpty() ? ThidaGui.MUTED : ThidaGui.GOLD);
            }
            ThidaGui.text(g, "ลูกกลิ้ง: เลื่อน  •  คลิกขวา / Esc: ย้อนกลับ", x + 10, y + h - 15, w - 20, ThidaGui.MUTED);
            super.render(g, mouseX, mouseY, partial);
        }
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void tooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        String item = ThidaEvents.id(event.getItemStack());
        if (ThidaTooltips.passive(item) || item.equals("parasite_doll")) {
            var row = ThidaHudState.snapshot().getCompound(item.equals("parasite_doll") ? "parasite" : item);
            var tag = ThidaItemData.read(event.getItemStack());
            String token = item.equals("parasite_doll") ? "thida_parasite_instance" : "thida_passive_instance";
            boolean linked = tag.hasUUID(token) && row.hasUUID("itemInstance") && tag.getUUID(token).equals(row.getUUID("itemInstance"));
            event.getToolTip().add(Component.literal(linked ? "เชื่อมกับ: " + row.getString("name") + " · " + row.getString("status") : "ยังไม่มีสายสัมพันธ์")
                    .withStyle(linked ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.DARK_GRAY));
        }
        if (!ThidaTooltips.passive(item) || !Screen.hasShiftDown()) return;
        var lines = event.getToolTip();
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                && text.getKey().equals(ThidaTooltips.MORE)) {
            lines.remove(i); var extra = new ArrayList<Component>(); ThidaTooltips.appendDetails(item, extra); lines.addAll(i, extra); return;
        }
    }
    private ThidaClient() {}
}

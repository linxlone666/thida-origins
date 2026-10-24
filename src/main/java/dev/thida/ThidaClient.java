package dev.thida;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

/** Client-only entry point: the server never loads Minecraft or Screen classes. */
@EventBusSubscriber(modid = "thida", value = Dist.CLIENT)
public final class ThidaClient {
    public static final KeyMapping OPEN_MENU = new KeyMapping("key.thida.open_doll_menu",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.thida");
    @EventBusSubscriber(modid = "thida", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class KeyRegistration {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) { event.register(OPEN_MENU); }
    }
    private static boolean doll(ItemStack stack) {
        return stack.is(ThidaMod.ABILITIES.get("nilakan_doll").get())
                || stack.is(ThidaMod.ABILITIES.get("narotra_doll").get());
    }
    @SubscribeEvent
    public static void key(ClientTickEvent.Pre event) {
        while (OPEN_MENU.consumeClick()) openMenu();
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void tooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        String item = ThidaEvents.id(event.getItemStack());
        if (!ThidaTooltips.passive(item) || !Screen.hasShiftDown()) return;
        var lines = event.getToolTip();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                    && text.getKey().equals(ThidaTooltips.MORE)) {
                lines.remove(i);
                var details = new java.util.ArrayList<Component>();
                ThidaTooltips.appendDetails(item, details);
                lines.addAll(i, details);
                return;
            }
        }
    }
    private static void openMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        ItemStack held = mc.player.getMainHandItem();
        if (!doll(held)) held = mc.player.getOffhandItem();
        if (!doll(held)) return;
        // A user may bind this to a number or mouse button. Suppress its competing game action.
        for (KeyMapping mapping : mc.options.keyMappings) if (mapping != OPEN_MENU && mapping.same(OPEN_MENU)) {
            while (mapping.consumeClick()) { }
            mapping.setDown(false);
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ThidaNetwork.DollAction(1));
        mc.setScreen(new DollScreen(held.is(ThidaMod.ABILITIES.get("narotra_doll").get())));
    }
    private static final class DollScreen extends Screen {
        private final boolean blessing;
        private final java.util.List<SkillButton> buttons = new java.util.ArrayList<>();
        private final String item;
        private int left, top;
        DollScreen(boolean blessing) {
            super(Component.literal(blessing ? "นรตรา" : "นิลกาล"));
            this.blessing=blessing;
            this.item=blessing?"narotra_doll":"nilakan_doll";
        }
        @Override protected void init() {
            buttons.clear(); left=(width-272)/2; top=Math.max(6,(height-150)/2);
            String name=blessing?"ยกเลิกสายสัมพันธ์":"ปลดสายสัมพันธ์";
            SkillButton widget=new SkillButton(left+6,top+76,260,35,"unlink",name,"ยกเลิกการเชื่อมเป้าหมาย",1,
                    blessing?0xFF91DAC2:0xFFD3A4EC,b->choose(1));
            buttons.add(addRenderableWidget(widget));
            refresh();
        }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void tick() { refresh(); }
        private void refresh() {
            var data=ThidaHudState.snapshot();
            boolean linked=!data.getCompound(item).getString("id").isEmpty();
            SkillButton button=buttons.get(0);
            button.active=data.getBoolean("thida")&&linked;
            button.status=!data.getBoolean("thida")?"ต้องเลือกเผ่ากำเนิดธิดา":!linked?"ยังไม่มีสายสัมพันธ์":button.description;
        }
        private void choose(int button) {
            refresh();
            if(button!=1||!buttons.get(0).active) return;
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ThidaNetwork.DollAction(5)); onClose();
        }
        @Override public boolean keyPressed(int key,int scan,int modifiers) {
            if(key==GLFW.GLFW_KEY_1) { choose(1); return true; }
            return super.keyPressed(key,scan,modifiers);
        }
        @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
            renderBackground(graphics, mouseX, mouseY, partialTick); refresh();
            int accent=blessing?0xFF91DAC2:0xFFD3A4EC;
            ThidaGui.frame(graphics,left,top,272,150,accent,true);
            ThidaGui.icon(graphics,blessing?"narotra":"nilakan",left+10,top+6,2);
            ThidaGui.text(graphics,title.getString()+(blessing?" · ฟื้นมานา":" · เสริมพลัง"),left+49,top+9,214,accent);
            var link=ThidaHudState.snapshot().getCompound(item);
            ThidaGui.text(graphics,link.getString("id").isEmpty()?"ถือหุ่นเพื่อเชื่อมเป้าหมาย":"ผูกกับ "+link.getString("name"),left+49,top+24,214,ThidaGui.TEXT);
            ThidaGui.text(graphics,"กด 1 เพื่อยกเลิกการเชื่อม",left+10,top+135,174,ThidaGui.MUTED);
            ThidaGui.text(graphics,"Esc ปิด",left+211,top+135,55,ThidaGui.GOLD);
            super.render(graphics,mouseX,mouseY,partialTick);
        }
    }
    private static final class SkillButton extends net.minecraft.client.gui.components.Button {
        private final String icon, description;
        private final int number, accent;
        private String status;
        SkillButton(int x,int y,int w,int h,String icon,String name,String description,int number,int accent,OnPress press) {
            super(x,y,w,h,Component.literal(name+" — "+description),press,DEFAULT_NARRATION);
            this.icon=icon; this.description=description; this.number=number; this.accent=accent; this.status=description;
        }
        @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float tick) {
            ThidaGui.frame(g,getX(),getY(),width,height,active?accent:0xFF796780,isHoveredOrFocused()&&active);
            ThidaGui.icon(g,icon,getX()+4,getY()+2,2);
            if(!active) g.fill(getX()+4,getY()+2,getX()+36,getY()+34,0x80211D2D);
            String title=getMessage().getString().split(" — ",2)[0];
            ThidaGui.text(g,title,getX()+43,getY()+6,width-76,active?ThidaGui.TEXT:ThidaGui.MUTED);
            ThidaGui.text(g,status,getX()+43,getY()+20,width-76,active?ThidaGui.MUTED:ThidaGui.GOLD);
            ThidaGui.frame(g,getX()+width-25,getY()+8,18,18,accent,isHoveredOrFocused());
            ThidaGui.text(g,Integer.toString(number),getX()+width-19,getY()+13,9,active?accent:ThidaGui.MUTED);
        }
    }
    private ThidaClient() {}
}

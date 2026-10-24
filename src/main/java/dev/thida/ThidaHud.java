package dev.thida;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "thida", value = Dist.CLIENT)
public final class ThidaHud {
    private static final int WHITE = 0xFFF0E9DA, GREEN = 0xFF9DE7AD, RED = 0xFFFFACAC, GOLD = 0xFFFFD780;
    private static final String[] LINKS = {"nilakan_doll", "narotra_doll", "parasite", "heart"};
    private static final String[] NAMES = {"นิลกาล", "นรตรา", "กาฝาก", "พันธะหทัย"};
    private static CompoundTag previous;
    private static long revision = -1;
    private static String notice = "";
    private static int noticeTicks;
    private static long previewUntil;
    public static void preview() {
        if (Minecraft.getInstance().level != null) previewUntil = Minecraft.getInstance().level.getGameTime() + 200;
    }
    private record Row(String text, int color) {}

    public static String cooldown(String key) {
        long ticks = ThidaHudState.remaining(ThidaHudState.snapshot().getCompound("cooldowns").getLong(key));
        return ticks > 0 ? ThidaHudState.time(ticks) : "พร้อม";
    }
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        ThidaHudState.accept(new CompoundTag()); previous = null; revision = -1; noticeTicks = 0; previewUntil = 0;
    }
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.player == null || mc.level == null || mc.isPaused()) return;
        ThidaHudState.tick();
        if (noticeTicks > 0) noticeTicks--;
        if (revision == ThidaHudState.revision()) return;
        revision = ThidaHudState.revision();
        CompoundTag next = ThidaHudState.snapshot();
        if (previous != null && previous.getString("player").equals(next.getString("player"))) detectChanges(previous, next);
        previous = next;
    }
    private static void detectChanges(CompoundTag old, CompoundTag next) {
        // Emit at most one cue per update, prioritizing protection and broken links.
        if (old.getLong("invulnerable") == 0 && next.getLong("invulnerable") > 0) {
            cue("อมตะ 5 วินาที", SoundEvents.TOTEM_USE, 1); return;
        }
        for (int i = 0; i < LINKS.length; i++) {
            String before = old.getCompound(LINKS[i]).getString("id");
            String after = next.getCompound(LINKS[i]).getString("id");
            if (!before.isEmpty() && after.isEmpty()) {
                cue(NAMES[i] + " • การเชื่อมสิ้นสุด", SoundEvents.ITEM_BREAK, 0.8F); return;
            }
            if (!after.isEmpty() && !before.equals(after)) {
                cue(NAMES[i] + " → " + next.getCompound(LINKS[i]).getString("name"), SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F); return;
            }
        }
        if (old.getLong("domain") == 0 && next.getLong("domain") > 0) {
            cue("กางโดเมนแล้ว • 5 นาที", SoundEvents.BEACON_ACTIVATE, 1); return;
        }
        if (old.getLong("domain") > 0 && next.getLong("domain") == 0) {
            cue("โดเมนสิ้นสุด • เริ่มคูลดาวน์", SoundEvents.BEACON_DEACTIVATE, 1); return;
        }
        if (!old.getBoolean("armed") && next.getBoolean("armed")) {
            cue("เสกกาฝากแล้ว • ตีเพื่อเชื่อม", SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F); return;
        }
        if (!old.getString("guardian").equals(next.getString("guardian"))) {
            cue(next.getString("guardian").isEmpty() ? "พันธะหัวใจสิ้นสุด" : "ผู้ดูแลหัวใจ: " + next.getString("guardian"),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, 1); return;
        }
        CompoundTag cds = next.getCompound("cooldowns"), prior = old.getCompound("cooldowns");
        for (String key : prior.getAllKeys()) if (next.getBoolean("thida") && prior.getLong(key) > 0 && cds.getLong(key) == 0) {
            cue(label(key) + " • พร้อมใช้", SoundEvents.PLAYER_LEVELUP, 1.4F); return;
        }
    }
    private static String label(String key) {
        for (int i = 0; i < LINKS.length; i++) if (key.contains(LINKS[i])) {
            if (key.startsWith("cd_effect_")) return NAMES[i] + " ปุ่ม " + (key.charAt(key.length() - 1) - '0' + 1);
            return NAMES[i];
        }
        return key.contains("domain") ? "โดเมน" : "สกิล";
    }
    private static void cue(String text, SoundEvent sound, float pitch) {
        notice = text; noticeTicks = 80;
        if (ThidaHudConfig.SOUNDS.get() && Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.playSound(sound, ThidaHudConfig.VOLUME.get().floatValue(), pitch);
    }
    private record Badge(String icon, String key, HudTimeline.Phase phase, long ticks, int total, boolean sustained) {}
    private static void add(List<Badge> badges,String icon,String key,long active,long cd,int activeTotal,int cdTotal,boolean sustained) {
        HudTimeline.Phase phase=HudTimeline.phase(active,cd,sustained);
        if(phase!=HudTimeline.Phase.HIDDEN) badges.add(new Badge(icon,key,phase,HudTimeline.shownTicks(active,cd),
                phase==HudTimeline.Phase.ACTIVE?activeTotal:cdTotal,sustained));
    }
    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.options.hideGui||mc.options.renderDebug||mc.screen!=null||!ThidaHudConfig.VISIBLE.get()) return;
        CompoundTag data=ThidaHudState.snapshot();
        if(!data.getString("player").equals(mc.player.getUUID().toString())) return;
        List<Badge> badges=new ArrayList<>();
        if(data.getBoolean("thida")) {
            String[][] effects={{"weak","bleed","vulnerable"},{"shield","cleanse","speed"}};
            String[] dolls={"nilakan_doll","narotra_doll"};
            for(int i=0;i<2;i++) {
                for(int mode=0;mode<3;mode++) {
                    String key=dolls[i]+"_"+mode;
                    add(badges,effects[i][mode],Integer.toString(mode+1),
                            ThidaHudState.remaining(data.getCompound("activeEffects").getLong(key)),
                            ThidaGui.remaining(data,"cd_effect_"+key),data.getInt("effectDuration"),data.getInt("effectTotal"),false);
                }
                // The ten-minute unlink cooldown is separate from each thirty-second spell cooldown.
                add(badges,i==0?"nilakan":"narotra","",0,ThidaGui.remaining(data,"cd_"+dolls[i]),0,data.getInt("releaseTotal"),false);
            }
            add(badges,"parasite","",0,ThidaGui.remaining(data,"cd_parasite_doll"),0,data.getInt("releaseTotal"),
                    data.getBoolean("armed")||!data.getCompound("parasite").getString("id").isEmpty());
            add(badges,"heart","",0,ThidaGui.remaining(data,"cd_heart_doll"),0,data.getInt("releaseTotal"),
                    !data.getCompound("heart").getString("id").isEmpty());
            add(badges,"domain","",ThidaHudState.remaining(data.getLong("domain")),ThidaGui.remaining(data,"cd_domain_talisman"),
                    data.getInt("domainTotal"),data.getInt("domainCooldownTotal"),false);
            if(data.getInt("gaze")>0) add(badges,"narotra","G",Math.max(1,data.getInt("gazeRequired")-data.getInt("gaze")),0,data.getInt("gazeRequired"),0,false);
        }
        if(!data.getString("guardian").isEmpty()) add(badges,"heart","",0,0,0,0,true);
        add(badges,"shield","!",ThidaHudState.remaining(data.getLong("invulnerable")),0,100,0,false);
        if (mc.level != null && previewUntil > mc.level.getGameTime()) {
            // Local placement sample only: no real effect, cooldown or network state is changed.
            badges.clear();
            long left = previewUntil - mc.level.getGameTime();
            add(badges,"speed","P",left+200,0,400,0,false);
            add(badges,"domain","P",0,left,0,200,false);
            add(badges,"heart","P",0,0,0,0,true);
        }
        if(badges.isEmpty()) return;
        int sw=mc.getWindow().getGuiScaledWidth(), sh=mc.getWindow().getGuiScaledHeight();
        int cols=Math.max(1,Math.min(ThidaHudConfig.ICONS_PER_ROW.get(),(sw-16)/38));
        int rows=(badges.size()+cols-1)/cols;
        int x=Math.min(ThidaHudConfig.X.get(),Math.max(0,sw-Math.min(cols,badges.size())*38-4));
        int y=Math.min(ThidaHudConfig.Y.get(),Math.max(0,sh-rows*39-4));
        var g=event.getGuiGraphics();
        for(int i=0;i<badges.size();i++) {
            Badge badge=badges.get(i);
            int bx=x+(i%cols)*38, by=y+(i/cols)*39;
            boolean active=badge.phase()==HudTimeline.Phase.ACTIVE;
            int color=active?0xFF91DAC2:ThidaGui.GOLD;
            ThidaGui.frame(g,bx,by,34,35,color,active);
            ThidaGui.icon(g,badge.icon(),bx+4,by+3,1);
            if(!active) g.fill(bx+4,by+3,bx+20,by+19,0x88211D2D);
            if(!active) ThidaGui.text(g,"CD",bx+21,by+3,12,color);
            if(!badge.key().isEmpty()) ThidaGui.text(g,badge.key(),bx+24,by+13,8,ThidaGui.MUTED);
            String timer=badge.sustained()?"∞":ThidaHudState.time(badge.ticks());
            int tx=bx+(34-mc.font.width(timer))/2;
            ThidaGui.text(g,timer,Math.max(bx+2,tx),by+22,30,color);
            float progress=badge.sustained()?1:active?badge.ticks()/(float)Math.max(1,badge.total()):ThidaGui.progress(badge.ticks(),badge.total());
            ThidaGui.bar(g,bx+3,by+32,28,progress,color);
        }
    }
    private ThidaHud() {}
}

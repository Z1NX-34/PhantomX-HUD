package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;

/**
 * Boss bars for every boss event the game knows about (including other mods' bosses that use the vanilla system): name
 * with red slashes and gold diamonds, slanted bar with HP numbers and a drain trail, and a thin posture bar below it
 * when Epic Fight Posture Integration reports posture for that boss.
 */
final class BossBars {
    private BossBars() {}

    private static final Logger LOG = LogManager.getLogger("PhantomXHud/Boss");
    private static final Pattern PHASE = Pattern.compile("(?i)phase\\s*([0-9ivx]+)");
    private static final Map<UUID, Float> TRAIL = new HashMap<>();
    private static final Map<UUID, Float> POSTURE = new HashMap<>();
    private static long lastNanos;

    private static Field eventsField;
    private static boolean eventsFailed;

    /** True when boss events cannot be read, in which case the vanilla bars must stay visible. */
    static boolean unavailable() {
        return eventsFailed;
    }

    @SuppressWarnings("unchecked")
    private static List<LerpingBossEvent> events(Minecraft mc) {
        List<LerpingBossEvent> out = new ArrayList<>();
        if (eventsFailed) return out;
        try {
            BossHealthOverlay overlay = mc.gui.getBossOverlay();
            if (eventsField == null) {
                for (Field f : BossHealthOverlay.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        eventsField = f;
                        break;
                    }
                }
                if (eventsField == null) throw new IllegalStateException("no map field in BossHealthOverlay");
            }
            Map<UUID, LerpingBossEvent> map = (Map<UUID, LerpingBossEvent>) eventsField.get(overlay);
            out.addAll(map.values());
        } catch (Throwable t) {
            eventsFailed = true;
            LOG.warn("Boss events unreadable, vanilla bars stay hidden: {}", t.toString());
        }
        return out;
    }

    static void renderAll(GuiGraphics g, int sw, int sh, float scale) {
        Minecraft mc = Minecraft.getInstance();
        List<LerpingBossEvent> list = events(mc);
        if (list.isEmpty()) {
            if (!TRAIL.isEmpty()) {
                TRAIL.clear();
                POSTURE.clear();
            }
            return;
        }
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;
        int max = HudConfig.BOSS_MAX.get();
        for (int i = 0; i < list.size() && i < max; i++) draw(g, mc, list.get(i), i, sw, sh, scale, dt);
    }

    private static String num(float v) {
        if (v > 0f && v < 1f) return "1";
        return String.format(Locale.ROOT, "%,d", Math.round(v));
    }

    /** Thin, minimal boss bar: name left, HP right, a slim slanted bar, and an even slimmer posture bar underneath. */
    private static void draw(GuiGraphics g, Minecraft mc, LerpingBossEvent ev, int index, int sw, int sh, float scale, float dt) {
        Font font = mc.font;
        PostureLink.Info info = PostureLink.lookup(mc, ev);
        LivingEntity ent = info.entity;

        // The bar's own progress is the truth, unless a boss whose name matches an entity says otherwise (some mods
        // keep the event progress at a fixed value and track the real health on the entity).
        float pct = PlayerBlock.clamp01(ev.getProgress());
        String hp = Math.round(pct * 100f) + "%";
        if (ent != null && ent.getMaxHealth() > 0f) {
            float ef = PlayerBlock.clamp01(ent.getHealth() / ent.getMaxHealth());
            if (info.byName && Math.abs(ef - pct) > 0.04f) pct = ef;
            hp = num(ent.getHealth()) + " / " + num(ent.getMaxHealth());
        }
        float trail = TRAIL.getOrDefault(ev.getId(), pct);
        if (pct >= trail) trail = pct; else trail = Math.max(pct, trail - dt * 0.30f);
        TRAIL.put(ev.getId(), trail);

        String name = ev.getName().getString();
        Matcher mt = PHASE.matcher(name);
        String phase = mt.find() ? "PHASE " + mt.group(1).toUpperCase(Locale.ROOT) : null;

        float w = Math.min(270f, sw / scale * 0.42f), h = 5.2f, sl = 3.4f;
        float nameScale = 0.92f, hpScale = 0.78f;
        float room = w - sl - font.width(hp) * hpScale - 16f - (phase == null ? 0f : 40f);
        String shown = name;
        while (shown.length() > 3 && font.width(shown) * nameScale > room) shown = shown.substring(0, shown.length() - 1);
        if (!shown.equals(name)) shown = shown.stripTrailing() + "...";

        float cx = sw / 2f, y = sh - 92f * scale - index * 34f * scale;
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1f);
        Matrix4f m = g.pose().last().pose();
        float x0 = -w / 2f, x1 = w / 2f, len = w - sl;

        int top, bot;
        switch (ev.getColor()) {
            case PINK -> { top = argb(238, 138, 176, 255); bot = argb(150, 56, 98, 255); }
            case BLUE -> { top = argb(126, 176, 232, 255); bot = argb(52, 92, 156, 255); }
            case GREEN -> { top = argb(132, 206, 124, 255); bot = argb(52, 122, 64, 255); }
            case YELLOW -> { top = argb(244, 210, 104, 255); bot = argb(172, 128, 40, 255); }
            case PURPLE -> { top = argb(176, 126, 236, 255); bot = argb(92, 58, 164, 255); }
            case WHITE -> { top = argb(242, 238, 228, 255); bot = argb(160, 152, 138, 255); }
            default -> { top = argb(222, 64, 62, 255); bot = argb(128, 22, 28, 255); }
        }

        // name (left) and phase tag, HP (right)
        g.pose().pushPose();
        g.pose().translate(x0 + sl + 0.5f, -11.2f, 0f);
        g.pose().scale(nameScale, nameScale, 1f);
        g.drawString(font, shown, 0, 0, BONE, true);
        if (phase != null) {
            g.pose().translate(font.width(shown) + 6f, 1.2f, 0f);
            g.pose().scale(0.72f, 0.72f, 1f);
            g.drawString(font, phase, 0, 0, GOLD, true);
        }
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(x1 - 0.5f, -10.3f, 0f);
        g.pose().scale(hpScale, hpScale, 1f);
        g.drawString(font, hp, -font.width(hp), 0, withAlpha(BONE, 225), true);
        g.pose().popPose();

        // main bar
        poly(m, withAlpha(GOLD, 150), skewBand(x0 - 0.7f, sl, h, -0.7f, h + 0.7f, 0f, len + 1.4f));
        poly(m, withAlpha(INK, 228), skewBand(x0, sl, h, 0f, h, 0f, len));
        if (trail > pct) poly(m, withAlpha(BONE, 215), skewBand(x0, sl, h, 0f, h, 0f, len * trail));
        if (pct > 0.002f) {
            float u = len * pct;
            polyColors(m, 0f, skewBand(x0, sl, h, 0f, h, 0f, u), new int[] {top, top, bot, bot});
            poly(m, withAlpha(WHITE, 90), skewBand(x0, sl, h, 0f, h * 0.3f, 0f, u));
        }
        for (int q = 1; q <= 3; q++) {
            float u = len * q / 4f;
            float[] t = skewBand(x0, sl, h, 0f, h, u, u);
            line(m, withAlpha(INK, 120), t[0], 0f, t[6], h, 0.6f);
        }
        diamondFill(m, GOLD, x0 - 3.2f, h / 2f, 1.7f, 1.7f);
        diamondFill(m, GOLD, x1 - sl / 2f + 3.2f, h / 2f, 1.7f, 1.7f);

        // posture bar
        if (info.posture >= 0f) {
            float pv = POSTURE.getOrDefault(ev.getId(), info.posture);
            pv += (info.posture - pv) * Math.min(1f, dt * 10f);
            POSTURE.put(ev.getId(), pv);
            float ph = 2.4f, py = h + 3.4f, psl = 1.6f, pw = w * 0.6f, plen = pw - psl, px0 = -pw / 2f;
            boolean pulse = info.broken && ((System.currentTimeMillis() / 140) % 2 == 0);
            int ptop = info.broken ? (pulse ? WHITE : RED) : pv > 0.82f ? argb(255, 140, 56, 255) : argb(226, 182, 88, 255);
            int pbot = info.broken ? (pulse ? WHITE : DRED) : pv > 0.82f ? argb(190, 70, 24, 255) : argb(160, 104, 36, 255);
            g.pose().pushPose();
            g.pose().translate(0f, py, 0f);
            Matrix4f pm = g.pose().last().pose();
            poly(pm, withAlpha(info.broken ? RED : GOLD, 120), skewBand(px0 - 0.5f, psl, ph, -0.5f, ph + 0.5f, 0f, plen + 1f));
            poly(pm, withAlpha(INK, 222), skewBand(px0, psl, ph, 0f, ph, 0f, plen));
            if (pv > 0.01f) polyColors(pm, 0f, skewBand(px0, psl, ph, 0f, ph, 0f, plen * pv), new int[] {ptop, ptop, pbot, pbot});
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate(px0 - 3f, py - 1.2f, 0f);
            g.pose().scale(0.5f, 0.5f, 1f);
            String lab = info.broken ? "BROKEN" : "POSTURE";
            g.drawString(font, lab, -font.width(lab), 0, info.broken ? RED : withAlpha(BONE, 170), true);
            g.pose().popPose();
        }
        g.pose().popPose();
    }
}

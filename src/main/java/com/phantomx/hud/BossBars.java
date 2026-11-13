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
        return String.format(Locale.ROOT, "%,d", Math.round(v));
    }

    private static void draw(GuiGraphics g, Minecraft mc, LerpingBossEvent ev, int index, int sw, int sh, float scale, float dt) {
        Font font = mc.font;
        PostureLink.Info info = PostureLink.lookup(mc, ev);
        LivingEntity ent = info.entity;

        float pct = PlayerBlock.clamp01(ev.getProgress());
        float trail = TRAIL.getOrDefault(ev.getId(), pct);
        if (pct >= trail) trail = pct; else trail = Math.max(pct, trail - dt * 0.30f);
        TRAIL.put(ev.getId(), trail);

        String name = ev.getName().getString();
        Matcher mt = PHASE.matcher(name);
        String phase = mt.find() ? "PHASE " + mt.group(1).toUpperCase(Locale.ROOT) : null;
        String hp = ent != null && ent.getMaxHealth() > 0f
                ? num(ent.getHealth()) + " / " + num(ent.getMaxHealth())
                : Math.round(pct * 100f) + "%";

        float w = Math.min(340f, sw / scale * 0.5f), h = 11f, sl = 6f;
        float cx = sw / 2f, y = sh - 98f * scale - index * 50f * scale;
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1f);
        Matrix4f m = g.pose().last().pose();
        float x0 = -w / 2f, x1 = w / 2f;

        int fill, dark;
        switch (ev.getColor()) {
            case PURPLE -> { fill = argb(150, 110, 214, 255); dark = argb(70, 50, 120, 255); }
            case WHITE -> { fill = BONE; dark = argb(150, 144, 130, 255); }
            case BLUE -> { fill = BLUE; dark = argb(52, 70, 104, 255); }
            default -> { fill = RED; dark = DRED; }
        }

        // name row: red slashes and gold diamonds either side
        int nameW = font.width(name);
        float ny = -19f, half = nameW / 2f + 14f;
        int slash = withAlpha(RED, 235);
        line(m, slash, -half - 26f, ny + 9f, -half - 14f, ny + 1f, 1.6f);
        line(m, slash, -half - 20f, ny + 9f, -half - 8f, ny + 1f, 1.6f);
        line(m, slash, half + 14f, ny + 9f, half + 26f, ny + 1f, 1.6f);
        line(m, slash, half + 8f, ny + 9f, half + 20f, ny + 1f, 1.6f);
        diamondFill(m, GOLD, -half - 2f, ny + 4.5f, 2.6f, 2.6f);
        diamondFill(m, GOLD, half + 2f, ny + 4.5f, 2.6f, 2.6f);
        g.drawString(font, name, Math.round(-nameW / 2f), Math.round(ny + 1f), BONE, true);
        if (phase != null) {
            g.pose().pushPose();
            g.pose().translate(x1 - sl, -8.4f, 0);
            g.pose().scale(0.65f, 0.65f, 1f);
            g.drawString(font, phase, -font.width(phase), 0, GOLD, true);
            g.pose().popPose();
        }

        // main bar (parallelogram)
        poly(m, withAlpha(GOLD, 215), x0 + sl - 0.9f, -0.9f, x1 + 0.9f, -0.9f, x1 - sl + 0.1f, h + 0.9f, x0 - 0.9f - 0.1f, h + 0.9f);
        poly(m, withAlpha(INK, 248), x0 + sl, 0, x1, 0, x1 - sl, h, x0, h);
        float ins = 1.2f, len = w - 2f * ins;
        if (trail > pct) {
            float xt = x0 + ins + len * trail;
            poly(m, withAlpha(BONE, 235), x0 + ins + sl * 0.9f, ins, xt, ins, xt - sl * 0.9f, h - ins, x0 + ins, h - ins);
        }
        if (pct > 0.002f) {
            float xf = x0 + ins + len * pct, ym = h * 0.5f;
            poly(m, fill, x0 + ins + sl * 0.9f, ins, xf, ins, xf - sl * 0.9f, h - ins, x0 + ins, h - ins);
            poly(m, withAlpha(dark, 210), x0 + ins + sl * 0.45f, ym, xf - sl * 0.45f, ym, xf - sl * 0.9f, h - ins, x0 + ins, h - ins);
        }
        float tx = x0 + ins + len * 0.5f + sl * 0.45f;
        line(m, withAlpha(GOLD, 235), tx + 1.5f, -1.5f, tx - 1.5f, h + 1.5f, 0.9f);
        // HP numbers
        g.pose().pushPose();
        float ts = 0.78f;
        g.pose().translate(0f + sl * 0.0f, (h - 7f * ts) / 2f, 0f);
        g.pose().scale(ts, ts, 1f);
        g.drawString(font, hp, Math.round(-font.width(hp) / 2f), 0, WHITE, true);
        g.pose().popPose();

        // posture bar
        if (info.posture >= 0f) {
            float pv = POSTURE.getOrDefault(ev.getId(), info.posture);
            pv += (info.posture - pv) * Math.min(1f, dt * 10f);
            POSTURE.put(ev.getId(), pv);
            float ph = 4.2f, py = h + 3f, pw = w * 0.74f, px0 = -pw / 2f, px1 = pw / 2f, ps = 3f;
            boolean pulse = info.broken && ((System.currentTimeMillis() / 140) % 2 == 0);
            int pcol = info.broken ? (pulse ? WHITE : RED) : pv > 0.82f ? argb(255, 128, 52, 255) : argb(218, 178, 96, 255);
            poly(m, withAlpha(info.broken ? RED : GOLD, 190), px0 + ps - 0.7f, py - 0.7f, px1 + 0.7f, py - 0.7f, px1 - ps + 0.1f, py + ph + 0.7f, px0 - 0.8f, py + ph + 0.7f);
            poly(m, withAlpha(INK, 245), px0 + ps, py, px1, py, px1 - ps, py + ph, px0, py + ph);
            float pin = 0.8f, plen = pw - 2f * pin;
            if (pv > 0.01f) {
                float pxf = px0 + pin + plen * pv;
                poly(m, pcol, px0 + pin + ps * 0.8f, py + pin, pxf, py + pin, pxf - ps * 0.8f, py + ph - pin, px0 + pin, py + ph - pin);
                poly(m, withAlpha(argb(255, 240, 200, 255), 110), px0 + pin + ps * 0.8f, py + pin, pxf, py + pin, pxf - ps * 0.2f, py + ph * 0.5f, px0 + pin + ps * 0.3f, py + ph * 0.5f);
            }
            // label
            g.pose().pushPose();
            g.pose().translate(px0 - 3f, py - 0.4f, 0f);
            g.pose().scale(0.55f, 0.55f, 1f);
            String lab = info.broken ? "BROKEN" : "POSTURE";
            g.drawString(font, lab, -font.width(lab), 0, info.broken ? RED : withAlpha(BONE, 190), true);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate(px1 + 3f, py - 0.4f, 0f);
            g.pose().scale(0.55f, 0.55f, 1f);
            g.drawString(font, Math.round(pv * 100f) + "%", 0, 0, withAlpha(BONE, 190), true);
            g.pose().popPose();
        } else {
            diamondFill(m, INK, 0, h + 8f, 4.6f, 4.6f);
            diamondLine(m, GOLD, 0, h + 8f, 4.6f, 4.6f, 0.8f);
            diamondFill(m, RED, 0, h + 8f, 1.7f, 1.7f);
        }
        g.pose().popPose();
    }
}

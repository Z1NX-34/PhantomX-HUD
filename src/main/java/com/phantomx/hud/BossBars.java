package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.joml.Matrix4f;

/** Boss bars: slanted bar with a drain trail, name flanked by red slashes and gold diamonds, phase label and tick, seal. */
final class BossBars {
    private BossBars() {}

    private static final Pattern PHASE = Pattern.compile("(?i)phase\\s*([0-9ivx]+)");
    private static final Map<UUID, Float> TRAIL = new HashMap<>();
    private static long lastNanos;

    static void draw(GuiGraphics g, LerpingBossEvent ev, int index, int sw, int sh, float scale) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        if (index == 0) lastNanos = now;

        float pct = PlayerBlock.clamp01(ev.getProgress());
        float trail = TRAIL.getOrDefault(ev.getId(), pct);
        if (pct >= trail) trail = pct; else trail = Math.max(pct, trail - dt * 0.30f);
        TRAIL.put(ev.getId(), trail);
        if (TRAIL.size() > 32) TRAIL.keySet().retainAll(java.util.Collections.singleton(ev.getId()));

        String name = ev.getName().getString();
        Matcher mt = PHASE.matcher(name);
        String phase = mt.find() ? "PHASE " + mt.group(1).toUpperCase() : null;

        float w = Math.min(320f, sw / scale * 0.5f), h = 8f, sl = 7f;
        float cx = sw / 2f, y = sh - 86f - index * 34f * scale;
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

        // name row
        int nameW = font.width(name);
        float ny = -17f;
        float half = nameW / 2f + 14f;
        line(m, withAlpha(RED, 235), -half - 26f, ny + 9f, -half - 14f, ny + 1f, 1.6f);
        line(m, withAlpha(RED, 235), -half - 20f, ny + 9f, -half - 8f, ny + 1f, 1.6f);
        line(m, withAlpha(RED, 235), half + 14f, ny + 9f, half + 26f, ny + 1f, 1.6f);
        line(m, withAlpha(RED, 235), half + 8f, ny + 9f, half + 20f, ny + 1f, 1.6f);
        diamondFill(m, GOLD, -half - 2f, ny + 4.5f, 2.6f, 2.6f);
        diamondFill(m, GOLD, half + 2f, ny + 4.5f, 2.6f, 2.6f);
        g.drawString(font, name, Math.round(-nameW / 2f), Math.round(ny + 1f), BONE, true);

        // bar
        poly(m, withAlpha(GOLD, 210), x0 - 1f, -1f, x1 + 1f, -1f, x1 - sl + 0.6f, h + 1f, x0 - 1f - 0.6f, h + 1f);
        poly(m, withAlpha(INK, 248), x0, 0, x1, 0, x1 - sl, h, x0 - 0.6f, h);
        float ins = 1.2f, len = w - 2f * ins;
        if (trail > pct) {
            float xt = x0 + ins + len * trail;
            poly(m, withAlpha(BONE, 235), x0 + ins, ins, xt, ins, xt - sl * 0.7f, h - ins, x0 + ins, h - ins);
        }
        if (pct > 0.002f) {
            float xf = x0 + ins + len * pct, ym = h * 0.5f;
            poly(m, fill, x0 + ins, ins, xf, ins, xf - sl * 0.7f, h - ins, x0 + ins, h - ins);
            poly(m, withAlpha(dark, 210), x0 + ins, ym, xf - sl * 0.35f, ym, xf - sl * 0.7f, h - ins, x0 + ins, h - ins);
        }
        // phase tick at half
        float tx = x0 + ins + len * 0.5f;
        line(m, withAlpha(GOLD, 235), tx, -2f, tx, h + 2f, 1f);
        diamondFill(m, GOLD, tx, h + 4f, 2f, 2f);

        // seal under the bar, centred
        diamondFill(m, INK, 0, h + 8.5f, 5f, 5f);
        diamondLine(m, GOLD, 0, h + 8.5f, 5f, 5f, 0.8f);
        diamondFill(m, RED, 0, h + 8.5f, 1.8f, 1.8f);

        if (phase != null) {
            g.pose().pushPose();
            g.pose().translate(x1 - sl, h + 4f, 0);
            g.pose().scale(0.7f, 0.7f, 1f);
            g.drawString(font, phase, -font.width(phase), 0, GOLD, true);
            g.pose().popPose();
        }
        g.pose().popPose();
    }
}

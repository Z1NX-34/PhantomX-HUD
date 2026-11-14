package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

/**
 * Status effects on the right edge, vertically centred. Each effect is a slanted dark tab with the icon, the time left,
 * the level in gold, a colour edge on the screen side and a thin bar that drains with the remaining duration. Seven per
 * column; more columns grow to the left. Flat and wide on purpose, so it never reads as the diamond skill icons.
 */
final class EffectsStrip {
    private EffectsStrip() {}

    private static final float W = 50f, H = 20f, GAP = 3f, SKEW = 4f;
    private static final int PER_COL = 7;
    private static final Map<MobEffect, Integer> MAX_DURATION = new HashMap<>();

    static void render(GuiGraphics g, Font font, Player p, int sw, int sh, float scale) {
        if (p.getActiveEffects().isEmpty()) {
            MAX_DURATION.clear();
            return;
        }
        List<MobEffectInstance> list = new ArrayList<>(p.getActiveEffects());
        Minecraft mc = Minecraft.getInstance();
        int n = Math.min(list.size(), PER_COL * 4);
        g.pose().pushPose();
        g.pose().translate(sw - 4f * scale, sh / 2f + HudConfig.EFFECTS_OFFSET_Y.get() * scale, 0);
        g.pose().scale(scale, scale, 1f);
        long ms = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            MobEffectInstance e = list.get(i);
            int col = i / PER_COL, row = i % PER_COL;
            int inCol = Math.min(PER_COL, n - col * PER_COL);
            float totalH = inCol * H + (inCol - 1) * GAP;
            float xr = -col * (W + 3f), xl = xr - W;
            float y = -totalH / 2f + row * (H + GAP);
            Matrix4f m = g.pose().last().pose();

            MobEffectCategory cat = e.getEffect().getCategory();
            int accent = cat == MobEffectCategory.HARMFUL ? argb(232, 66, 66, 255)
                    : cat == MobEffectCategory.BENEFICIAL ? argb(104, 214, 176, 255) : GOLD;

            // body
            poly(m, withAlpha(GOLD, 120), xl + SKEW - 0.8f, y - 0.8f, xr, y - 0.8f, xr, y + H + 0.8f, xl - 0.8f, y + H + 0.8f);
            polyColors(m, 0f, new float[] {xl + SKEW, y, xr, y, xr, y + H, xl, y + H},
                    new int[] {argb(16, 14, 22, 232), argb(32, 28, 44, 244), argb(32, 28, 44, 244), argb(14, 12, 18, 232)});
            line(m, withAlpha(accent, 230), xl + SKEW, y, xl, y + H, 1.2f);
            rect(m, accent, xr - 2f, y, xr, y + H);

            // icon, time, level
            TextureAtlasSprite sprite = mc.getMobEffectTextures().get(e.getEffect());
            int ix = Math.round(xl + SKEW + 1.5f), iy = Math.round(y + 1.5f);
            g.blit(ix, iy, 0, 16, 16, sprite);

            boolean infinite = e.isInfiniteDuration();
            String t = MobEffectUtil.formatDuration(e, 1.0f).getString();
            boolean low = !infinite && e.getDuration() <= 200;
            int tc = WHITE;
            if (low) {
                float pulse = 0.55f + 0.45f * (float) Math.sin(ms / 110.0);
                tc = argb(255, Math.round(120 + 80 * pulse), Math.round(110 + 60 * pulse), 255);
            }
            float ts = 0.78f;
            g.pose().pushPose();
            g.pose().translate(ix + 19f, y + (H - 7f * ts) / 2f + 0.5f, 0);
            g.pose().scale(ts, ts, 1f);
            g.drawString(font, t, 0, 0, tc, true);
            g.pose().popPose();
            if (e.getAmplifier() > 0) {
                String a = roman(e.getAmplifier() + 1);
                g.pose().pushPose();
                g.pose().translate(xr - 4f, y + 2.2f, 0);
                g.pose().scale(0.6f, 0.6f, 1f);
                g.drawString(font, a, -font.width(a), 0, GOLD, true);
                g.pose().popPose();
            }

            // remaining-duration bar
            float frac = 1f;
            if (!infinite) {
                int max = Math.max(e.getDuration(), MAX_DURATION.getOrDefault(e.getEffect(), 0));
                MAX_DURATION.put(e.getEffect(), max);
                frac = Math.min(1f, e.getDuration() / (float) Math.max(1, max));
            }
            float bx0 = xl + 4.2f, bx1 = xr - 3.5f, by = y + H - 2.1f;
            rect(m, withAlpha(INK, 230), bx0, by, bx1, by + 1.1f);
            if (frac > 0.005f) rect(m, withAlpha(accent, 230), bx0, by, bx0 + (bx1 - bx0) * frac, by + 1.1f);
        }
        g.pose().popPose();
    }

    private static String roman(int v) {
        if (v <= 0) return "";
        if (v > 20) return String.valueOf(v);
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX"};
        return r[v];
    }
}

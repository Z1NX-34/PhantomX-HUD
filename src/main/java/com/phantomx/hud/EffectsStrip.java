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
 * Status effects on the right edge, vertically centred. Each effect is a diamond at the very right holding the effect
 * icon (the level as a number on its lower corner) and, to its left, a slim slanted plate that is thinner than the
 * diamond: the time left in text and a bar that drains with the remaining duration. Seven per column; more columns grow
 * to the left.
 */
final class EffectsStrip {
    private EffectsStrip() {}

    private static final float HS = 10f, RH = 5.6f, RECT_W = 38f, GAP = 2.4f, PG = 2.2f;
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
        float pitch = 2f * HS + GAP, colW = RECT_W + PG + 2f * HS + 4f;
        g.pose().pushPose();
        g.pose().translate(sw - 4f * scale, sh / 2f + HudConfig.EFFECTS_OFFSET_Y.get() * scale, 0);
        g.pose().scale(scale, scale, 1f);
        long ms = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            MobEffectInstance e = list.get(i);
            int col = i / PER_COL, row = i % PER_COL;
            int inCol = Math.min(PER_COL, n - col * PER_COL);
            float totalH = inCol * pitch - GAP;
            float xd = -col * colW - HS - 1.5f;                     // diamond centre
            float yc = -totalH / 2f + row * pitch + HS;

            MobEffectCategory cat = e.getEffect().getCategory();
            int accent = cat == MobEffectCategory.HARMFUL ? argb(232, 66, 66, 255)
                    : cat == MobEffectCategory.BENEFICIAL ? argb(104, 214, 176, 255) : GOLD;
            boolean infinite = e.isInfiniteDuration();
            boolean low = !infinite && e.getDuration() <= 200;

            // ---------------- the time plate, left of the diamond and thinner than it
            float xc = xd - (HS - RH) - PG;                         // right corners of the plate
            float xn = xd - HS - PG;                                // notch that hugs the diamond's left tip
            float xl = xc - RECT_W, sk = 3f;                        // left end leans like the bars
            Matrix4f m = g.pose().last().pose();
            float t = yc - RH, b = yc + RH;
            // frame
            poly(m, withAlpha(GOLD, 150), xl + sk - 0.8f, t - 0.8f, xc + 0.8f, t - 0.8f, xn - 0.5f, yc, xl + 0.7f, yc);
            poly(m, withAlpha(GOLD, 150), xl + 0.7f, yc, xn - 0.5f, yc, xc + 0.8f, b + 0.8f, xl - 0.8f, b + 0.8f);
            // body
            polyColors(m, 0f, new float[] {xl + sk, t, xc, t, xn, yc, xl + sk / 2f, yc},
                    new int[] {argb(34, 30, 52, 246), argb(34, 30, 52, 246), argb(24, 22, 38, 246), argb(24, 22, 38, 246)});
            polyColors(m, 0f, new float[] {xl + sk / 2f, yc, xn, yc, xc, b, xl, b},
                    new int[] {argb(24, 22, 38, 246), argb(24, 22, 38, 246), argb(16, 14, 24, 246), argb(16, 14, 24, 246)});
            line(m, withAlpha(accent, 235), xl + sk, t, xl, b, 1.2f);

            // time text
            String tm = MobEffectUtil.formatDuration(e, 1.0f).getString();
            int tc = WHITE;
            if (low) {
                float pulse = 0.55f + 0.45f * (float) Math.sin(ms / 110.0);
                tc = argb(255, Math.round(120 + 80 * pulse), Math.round(110 + 60 * pulse), 255);
            }
            float ts = 0.72f;
            g.pose().pushPose();
            g.pose().translate(xl + sk + 3.4f, t + 1.0f, 0f);
            g.pose().scale(ts, ts, 1f);
            g.drawString(font, tm, 0, 0, tc, true);
            g.pose().popPose();

            // draining bar along the bottom of the plate
            float frac = 1f;
            if (!infinite) {
                int max = Math.max(e.getDuration(), MAX_DURATION.getOrDefault(e.getEffect(), 0));
                MAX_DURATION.put(e.getEffect(), max);
                frac = Math.min(1f, e.getDuration() / (float) Math.max(1, max));
            }
            float by0 = b - 2.7f, by1 = b - 1.4f;
            float bx0 = xl + sk / 2f + 3.4f, bx1 = xc - 2.4f;
            rect(m, withAlpha(INK, 230), bx0, by0, bx1, by1);
            if (frac > 0.005f) rect(m, withAlpha(accent, 235), bx0, by0, bx0 + (bx1 - bx0) * frac, by1);

            // ---------------- the diamond with the icon
            diamondFill(m, withAlpha(INK, 240), xd, yc, HS, HS);
            diamondFill(m, withAlpha(argb(44, 40, 82, 255), 235), xd, yc, HS - 1.4f, HS - 1.4f);
            TextureAtlasSprite sprite = mc.getMobEffectTextures().get(e.getEffect());
            beginDiamondClip(g, xd, yc, HS - 1f, HS + 14f);
            g.pose().pushPose();
            g.pose().translate(xd - 7.6f, yc - 7.6f, 0f);
            g.pose().scale(15.2f / 18f, 15.2f / 18f, 1f);
            g.blit(0, 0, 0, 18, 18, sprite);
            g.pose().popPose();
            endDiamondClip(g);
            m = g.pose().last().pose();
            diamondLine(m, GOLD, xd, yc, HS, HS, 1.1f);
            diamondLine(m, withAlpha(accent, 200), xd, yc, HS - 2.1f, HS - 2.1f, 0.5f);

            // level as a plain number on the lower corner
            if (e.getAmplifier() > 0) {
                String lv = String.valueOf(e.getAmplifier() + 1);
                float ls = 0.72f;
                g.pose().pushPose();
                g.pose().translate(xd + HS * 0.5f - font.width(lv) * ls * 0.5f + 1.2f, yc + HS * 0.18f, 0f);
                g.pose().scale(ls, ls, 1f);
                g.drawString(font, lv, 0, 0, GOLD, true);
                g.pose().popPose();
            }
        }
        g.pose().popPose();
    }
}

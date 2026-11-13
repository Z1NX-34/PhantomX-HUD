package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

/**
 * Status effects on the right edge: small square tiles with a coloured top stripe and the time underneath. Rows stack
 * upward, extra columns grow to the left. Square and flat on purpose, so they never read as the diamond skill icons.
 */
final class EffectsStrip {
    private EffectsStrip() {}

    private static final float TILE = 17f, TEXT_H = 7f, GAP = 3f;

    static void render(GuiGraphics g, Font font, Player p, int sw, int sh, float scale) {
        if (p.getActiveEffects().isEmpty()) return;
        List<MobEffectInstance> list = new ArrayList<>(p.getActiveEffects());
        Minecraft mc = Minecraft.getInstance();
        g.pose().pushPose();
        g.pose().translate(sw - 8f * scale, sh - HudConfig.EFFECTS_Y.get() * scale, 0);
        g.pose().scale(scale, scale, 1f);
        int perCol = 6;
        for (int i = 0; i < list.size() && i < 24; i++) {
            MobEffectInstance e = list.get(i);
            float x = -TILE - (i / perCol) * (TILE + GAP + 1f);
            float y = -((i % perCol) + 1) * (TILE + TEXT_H + GAP) + GAP;
            Matrix4f m = g.pose().last().pose();
            boolean good = e.getEffect().isBeneficial();
            int accent = good ? argb(120, 190, 150, 255) : RED;
            float h = TILE + TEXT_H;
            rect(m, withAlpha(accent, 180), x - 0.7f, y - 0.7f, x + TILE + 0.7f, y + h + 0.7f);
            rect(m, withAlpha(INK, 242), x, y, x + TILE, y + h);
            rect(m, accent, x, y, x + TILE, y + 1.6f);
            TextureAtlasSprite sprite = mc.getMobEffectTextures().get(e.getEffect());
            g.blit(Math.round(x + 0.5f), Math.round(y + 2f), 0, 16, 16, sprite);
            String t = MobEffectUtil.formatDuration(e, 1.0f).getString();
            g.pose().pushPose();
            g.pose().translate(x + TILE / 2f, y + TILE + 0.8f, 0);
            g.pose().scale(0.6f, 0.6f, 1f);
            g.drawString(font, t, Math.round(-font.width(t) / 2f), 0, BONE, true);
            g.pose().popPose();
            if (e.getAmplifier() > 0) {
                g.pose().pushPose();
                g.pose().translate(x + TILE - 1f, y + 2.2f, 0);
                g.pose().scale(0.6f, 0.6f, 1f);
                String a = String.valueOf(e.getAmplifier() + 1);
                g.drawString(font, a, -font.width(a), 0, GOLD, true);
                g.pose().popPose();
            }
        }
        g.pose().popPose();
    }
}

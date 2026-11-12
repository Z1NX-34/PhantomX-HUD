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

/** Right edge: potion effects as small framed diamonds-corner tiles, gold for good effects, red for bad ones. */
final class EffectsStrip {
    private EffectsStrip() {}

    static void render(GuiGraphics g, Font font, Player p, int sw, int sh, float scale) {
        if (p.getActiveEffects().isEmpty()) return;
        List<MobEffectInstance> list = new ArrayList<>(p.getActiveEffects());
        Minecraft mc = Minecraft.getInstance();
        g.pose().pushPose();
        g.pose().translate(sw - 12f, sh - HudConfig.EFFECTS_Y.get(), 0);
        g.pose().scale(scale, scale, 1f);
        float size = 20f, gapY = 5f;
        for (int i = 0; i < list.size() && i < 10; i++) {
            MobEffectInstance e = list.get(i);
            float x = -size - (i / 5) * (size + 5f), y = -(i % 5) * (size + 9f + gapY);
            Matrix4f m = g.pose().last().pose();
            boolean good = e.getEffect().isBeneficial();
            int border = good ? GOLD : RED;
            float h = size + 9f;
            // body with cut corners (top-left, bottom-right)
            float c = 3f;
            poly(m, withAlpha(border, 200), x - 0.8f, y - 0.8f + c, x + c, y - 0.8f, x + size + 0.8f, y - 0.8f, x + size + 0.8f, y + h - c, x + size - c, y + h + 0.8f, x - 0.8f, y + h + 0.8f);
            poly(m, withAlpha(INK, 240), x, y + c, x + c, y, x + size, y, x + size, y + h - c, x + size - c, y + h, x, y + h);
            TextureAtlasSprite sprite = mc.getMobEffectTextures().get(e.getEffect());
            g.blit(Math.round(x + 2f), Math.round(y + 2f), 0, 16, 16, sprite);
            String t = MobEffectUtil.formatDuration(e, 1.0f).getString();
            g.pose().pushPose();
            g.pose().translate(x + size / 2f, y + size + 1.2f, 0);
            g.pose().scale(0.6f, 0.6f, 1f);
            g.drawString(font, t, Math.round(-font.width(t) / 2f), 0, BONE, true);
            g.pose().popPose();
            int amp = e.getAmplifier();
            if (amp > 0) {
                g.pose().pushPose();
                g.pose().translate(x + size - 1f, y + 1f, 0);
                g.pose().scale(0.6f, 0.6f, 1f);
                String a = String.valueOf(amp + 1);
                g.drawString(font, a, -font.width(a), 0, GOLD, true);
                g.pose().popPose();
            }
        }
        g.pose().popPose();
    }
}

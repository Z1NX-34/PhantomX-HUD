package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

/**
 * Health of the mount you ride (horse, donkey, pig, any modded mount, dragons...): a slim slanted bar in the bottom
 * centre, just above the vanilla jump / charge bar, with the mount's name above its left end. The vanilla (and modded)
 * mount health overlay is moved off-screen while this is on, so the two never overlap.
 */
final class MountBar {
    private MountBar() {}

    private static final float H = 5.6f, LEN = 150f;

    static void render(GuiGraphics g, Font font, Player p, int sw, int sh, float scale) {
        if (!(p.getVehicle() instanceof LivingEntity mount)) return;
        float max = mount.getMaxHealth();
        if (max <= 0f) return;
        float frac = PlayerBlock.clamp01(mount.getHealth() / max);

        g.pose().pushPose();
        // the jump / charge bar's top edge is at sh - 29; a dragon's own flame meter (from sh - 35) lifts it higher
        g.pose().translate(sw / 2f, sh - 29f - 4f - DragonBars.mountLift(mount) * scale, 0f);
        g.pose().scale(scale, scale, 1f);
        Matrix4f m = g.pose().last().pose();
        float y0 = -H, y1 = 0f, xl = -LEN / 2f + H / 2f, xr = xl + LEN;   // x at the bar's top edge; every edge leans down-left

        poly(m, withAlpha(GOLD, 210), lean(xl - 1.2f, xr + 2.2f, y0, y0 - 0.9f, y1 + 0.9f));
        poly(m, withAlpha(INK, 248), lean(xl, xr, y0, y0, y1));
        float ins = 1.1f, insX = ins * 1.45f;
        float xf = xl + insX + (LEN - 2f * insX) * frac;
        if (frac > 0.002f) {
            float[] q = lean(xl + insX, xf, y0, y0 + ins, y1 - ins);
            polyColors(m, 0f, q, new int[] {argb(226, 152, 62, 255), argb(226, 152, 62, 255), argb(130, 72, 26, 255), argb(130, 72, 26, 255)});
            poly(m, withAlpha(WHITE, 70), lean(xl + insX, xf, y0, y0 + ins, y0 + ins + 1f));
        }
        line(m, withAlpha(GOLD, 230), xl, y0, xr, y0, 0.7f);

        String hp = Math.round(mount.getHealth()) + " / " + Math.round(max);
        float ts = 0.52f;
        g.pose().pushPose();
        g.pose().translate((xl + xr) / 2f - H / 2f, y0 + (H - 7f * ts) / 2f, 0f);
        g.pose().scale(ts, ts, 1f);
        g.drawString(font, hp, Math.round(-font.width(hp) / 2f), 0, WHITE, true);
        g.pose().popPose();

        String name = mount.getDisplayName().getString();
        float ns = 0.62f;
        g.pose().pushPose();
        g.pose().translate(xl - 2f, y0 - 7f * ns - 2.6f, 0f);
        g.pose().scale(ns, ns, 1f);
        g.drawString(font, name, 0, 0, argb(236, 214, 170, 255), true);
        g.pose().popPose();
        g.pose().popPose();
    }

    /** Parallelogram between xl..xr at the top edge y0, leaning down-left, covering ya..yb. */
    private static float[] lean(float xl, float xr, float y0, float ya, float yb) {
        return new float[] {xl - (ya - y0), ya, xr - (ya - y0), ya, xr - (yb - y0), yb, xl - (yb - y0), yb};
    }
}

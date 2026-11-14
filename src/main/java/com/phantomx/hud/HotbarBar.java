package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * The hotbar in the PhantomX style: a dark slanted panel with gold edges, hairline slanted dividers, and the selected
 * slot lit in gold with a small diamond notch. Same position and slot layout as the vanilla bar, so everything else
 * (XP bar, item name, slot cluster) keeps lining up. Items are drawn here; the vanilla bar is moved off-screen.
 */
final class HotbarBar {
    private HotbarBar() {}

    private static final float SKEW = 3f, TOP = -22f;

    static void render(GuiGraphics g, Font font, Player p, int sw, int sh, float scale, float partial) {
        g.pose().pushPose();
        g.pose().translate(sw / 2f, sh, 0);
        g.pose().scale(scale, scale, 1f);
        Matrix4f m = g.pose().last().pose();

        // panel
        poly(m, withAlpha(GOLD, 70), -91f + SKEW - 1.2f, TOP - 1.2f, 91f + 1.2f, TOP - 1.2f, 91f - SKEW + 1.2f, 0f, -91f - 1.2f, 0f);
        polyColors(m, 0f, new float[] {-91f + SKEW, TOP, 91f, TOP, 91f - SKEW, 0f, -91f, 0f},
                new int[] {argb(32, 28, 46, 232), argb(32, 28, 46, 232), argb(12, 10, 16, 244), argb(12, 10, 16, 244)});
        line(m, withAlpha(PURP, 90), -91f + SKEW + 0.6f, TOP + 2.2f, 91f - 0.6f, TOP + 2.2f, 0.6f);
        line(m, withAlpha(GOLD, 235), -91f + SKEW, TOP, 91f, TOP, 1.3f);
        line(m, withAlpha(GOLD, 200), -91f + SKEW, TOP, -91f, 0f, 1.0f);
        line(m, withAlpha(GOLD, 200), 91f, TOP, 91f - SKEW, 0f, 1.0f);
        diamondFill(m, INK, -91f + SKEW, TOP, 2.8f, 2.8f);
        diamondFill(m, GOLD, -91f + SKEW, TOP, 2f, 2f);
        diamondFill(m, INK, 91f, TOP, 2.8f, 2.8f);
        diamondFill(m, GOLD, 91f, TOP, 2f, 2f);

        // dividers
        for (int i = 1; i < 9; i++) {
            float xs = -90f + 20f * i;
            line(m, withAlpha(GOLD, 52), xs + 1.4f, TOP + 1.8f, xs - 1.4f, -1.2f, 0.7f);
        }

        // selected slot
        int sel = p.getInventory().selected;
        float a = -90f + 20f * sel, b = a + 20f;
        float t0 = TOP + 1.4f, t1 = -0.8f;
        poly(m, withAlpha(GOLD, 38), a + 1.4f, t0, b + 1.4f, t0, b - 1.4f, t1, a - 1.4f, t1);
        line(m, GOLD, a + 1.4f, t0, b + 1.4f, t0, 1.0f);
        line(m, GOLD, a + 1.4f, t0, a - 1.4f, t1, 1.0f);
        line(m, GOLD, b + 1.4f, t0, b - 1.4f, t1, 1.0f);
        line(m, argb(255, 226, 140, 255), a - 1.4f, t1, b - 1.4f, t1, 1.5f);
        float ncx = (a + b) / 2f + 1.4f;
        diamondFill(m, INK, ncx, TOP, 3.6f, 3.6f);
        diamondFill(m, argb(255, 226, 140, 255), ncx, TOP, 2.6f, 2.6f);

        // items
        for (int i = 0; i < 9; i++) {
            ItemStack stack = p.getInventory().items.get(i);
            if (stack.isEmpty()) continue;
            int ix = Math.round(-88f + 20f * i), iy = -19;
            float pop = stack.getPopTime() - partial;
            g.pose().pushPose();
            if (pop > 0f) {
                float f = 1f + pop / 5f;
                g.pose().translate(ix + 8, iy + 12, 0f);
                g.pose().scale(1f / f, (f + 1f) / 2f, 1f);
                g.pose().translate(-(ix + 8), -(iy + 12), 0f);
            }
            g.renderItem(p, stack, ix, iy, i + 1);
            g.renderItemDecorations(font, stack, ix, iy);
            g.pose().popPose();
        }
        g.pose().popPose();
    }
}

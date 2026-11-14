package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

/**
 * Diamond slots around the crest (drawn in the player block's pose):
 * the Epic Fight special move on the crest's top-left edge (always there, a default move icon when there is none), and
 * a row of three refill diamonds above the health bar: mana, armor, hunger. Each fills from the bottom with its colour
 * and shows current over max inside; big numbers shorten (1.2k, 15k, 1.5M) and shrink to fit.
 */
final class GaugeSlots {
    private GaugeSlots() {}

    private static final float HS_SPECIAL = 19.2f * 0.8f;
    private static final float HS_MANA = 11.7f * 0.95f, HS_SMALL = 11.7f * 0.8f;
    private static final float GAP = 2.4f;

    static void render(GuiGraphics g, Font font, Player p, float partial) {
        if (HudConfig.SHOW_SKILLS.get()) special(g, partial);
        if (HudConfig.SHOW_CHIPS.get()) refillRow(g, font, p);
    }

    // ------------------------------------------------------------------------------------------ special move

    private static void special(GuiGraphics g, float partial) {
        float cx = PlayerBlock.CX, cy = PlayerBlock.CY, rg = PlayerBlock.R + 3f;
        // hugs the crest's top-left edge, nudged a little towards the top (to the right)
        float s = cx + cy - rg - HS_SPECIAL - GAP, d = (cx - cy) + 8f;
        float x = (s + d) / 2f, y = (s - d) / 2f, hs = HS_SPECIAL;

        Object innate = null;
        for (Object c : EpicFightSkills.visible()) {
            if (EpicFightSkills.isInnate(c)) {
                innate = c;
                break;
            }
        }
        Matrix4f m = g.pose().last().pose();
        diamondFill(m, withAlpha(INK, 238), x, y, hs, hs);
        float hi = hs - 2.2f;
        int centre = withAlpha(argb(78, 66, 150, 255), innate != null ? 235 : 150);
        int rim = withAlpha(argb(20, 18, 46, 255), 235);
        polyColors(m, 0f, new float[] {x, y, x, y - hi, x + hi, y, x, y + hi, x - hi, y, x, y - hi}, new int[] {centre, rim, rim, rim, rim, rim});

        if (innate != null) {
            float hm = hs - 1.3f;
            beginDiamondClip(g, x, y, hm, hs + 18f);
            EpicFightSkills.draw(g, innate, x, y, hm * 1.5f, partial);
            endDiamondClip(g);
        } else {
            defaultMove(m, x, y, hs / 12f * 0.95f);
        }
        m = g.pose().last().pose();
        diamondLine(m, GOLD, x, y, hs, hs, 1.3f);
        diamondLine(m, withAlpha(PURP, 190), x, y, hs - 2.6f, hs - 2.6f, 0.55f);
        diamondFill(m, GOLD, x - hs - 1.2f, y, 1.7f, 1.7f);
        diamondFill(m, GOLD, x + hs + 1.2f, y, 1.7f, 1.7f);
    }

    /** The default special-move icon: two crossing slashes with a bright spark. */
    private static void defaultMove(Matrix4f m, float x, float y, float s) {
        line(m, argb(122, 208, 240, 255), x - 5.2f * s, y - 5.2f * s, x + 5.4f * s, y + 5.4f * s, 1.7f * s);
        line(m, argb(191, 244, 255, 255), x - 6f * s, y + 5.2f * s, x + 6f * s, y - 5.6f * s, 2.3f * s);
        ellipse(m, WHITE, x, y, 1.5f * s, 1.5f * s);
    }

    // ------------------------------------------------------------------------------------------ refill diamonds

    private static void refillRow(GuiGraphics g, Font font, Player p) {
        float cx = PlayerBlock.CX, cy = PlayerBlock.CY, rg = PlayerBlock.R + 3f;
        float bottom = cy + 3f - GAP;                      // 3 = the health bar's top, relative to the crest centre

        float ym = bottom - HS_MANA;
        float xm = cx + rg - Math.abs(ym - cy) + HS_MANA + GAP + 0.2f;
        float[] mana = ResourceSources.mana();
        refill(g, font, xm, ym, HS_MANA, 0, mana == null ? 0f : mana[0] / mana[1], argb(74, 134, 232, 255), argb(34, 70, 150, 255),
                mana == null ? "-" : fmt(mana[0]), mana == null ? "" : "/" + fmt(mana[1]), mana != null);

        float ys = bottom - HS_SMALL;
        float xa = xm + HS_MANA + HS_SMALL + GAP;
        int armor = p.getArmorValue();
        int armorMax = Math.max(20, (int) Math.ceil(armor / 10.0) * 10);
        refill(g, font, xa, ys, HS_SMALL, 1, armor / (float) armorMax, argb(143, 180, 236, 255), argb(70, 96, 150, 255),
                fmt(armor), "/" + fmt(armorMax), true);

        float xh = xa + 2f * HS_SMALL + GAP;
        int food = p.getFoodData().getFoodLevel();
        refill(g, font, xh, ys, HS_SMALL, 2, food / 20f, argb(236, 148, 62, 255), argb(150, 80, 30, 255),
                fmt(food), "/20", true);
    }

    /** kind: 0 mana crystal, 1 chestplate, 2 drumstick. */
    private static void refill(GuiGraphics g, Font font, float x, float y, float hs, int kind, float frac, int col, int dark,
            String cur, String max, boolean live) {
        Matrix4f m = g.pose().last().pose();
        frac = PlayerBlock.clamp01(frac);
        diamondFill(m, withAlpha(INK, 240), x, y, hs, hs);
        diamondFill(m, withAlpha(argb(26, 28, 52, 255), 235), x, y, hs - 1f, hs - 1f);

        // faint icon behind the numbers
        g.pose().pushPose();
        g.pose().translate(x, y - 0.5f, 0f);
        float is = hs / 8.6f;
        g.pose().scale(is, is, 1f);
        Matrix4f mi = g.pose().last().pose();
        if (kind == 0) crystal(mi); else if (kind == 1) chestplate(mi); else drumstick(mi);
        g.pose().popPose();
        m = g.pose().last().pose();
        diamondFill(m, withAlpha(argb(26, 28, 52, 255), 150), x, y, hs - 1f, hs - 1f);

        // liquid, rising from the bottom vertex
        if (live && frac > 0.004f) {
            float h = hs - 1f, ly = h - 2f * h * frac;       // surface height relative to the centre
            float[] xy;
            if (ly >= 0f) xy = new float[] {x - (h - ly), y + ly, x + (h - ly), y + ly, x, y + h};
            else xy = new float[] {x - (h + ly), y + ly, x + (h + ly), y + ly, x + h, y, x, y + h, x - h, y};
            int top = withAlpha(col, 175), bot = withAlpha(dark, 205);
            int[] cols = xy.length == 6 ? new int[] {top, top, bot} : new int[] {top, top, top, bot, top};
            polyColors(m, 0f, xy, cols);
            float sw = ly >= 0f ? h - ly : h + ly;
            line(m, withAlpha(WHITE, 150), x - sw + 0.6f, y + ly, x + sw - 0.6f, y + ly, 0.6f);
        }
        diamondLine(m, live ? GOLD : DGOLD, x, y, hs, hs, 1.1f);
        diamondLine(m, withAlpha(PURP, 200), x, y, hs - 1.9f, hs - 1.9f, 0.45f);

        // numbers: current on top, max small underneath, both shrink to fit the diamond
        text(g, font, cur, x, y - 2.3f, 0.74f, 2f * (hs - 4.8f), WHITE);
        if (!max.isEmpty()) text(g, font, max, x, y + 3.1f, 0.5f, 2f * (hs - 5.3f), argb(230, 223, 203, 255));
    }

    /** Draws text centred on (x, yc) at a scale that fits within availW (never above base). */
    private static void text(GuiGraphics g, Font font, String t, float x, float yc, float base, float availW, int color) {
        float w = Math.max(1, font.width(t));
        float ts = Math.min(base, availW / w);
        g.pose().pushPose();
        g.pose().translate(x, yc - 3.5f * ts, 0f);
        g.pose().scale(ts, ts, 1f);
        g.drawString(font, t, Math.round(-w / 2f), 0, color, true);
        g.pose().popPose();
    }

    /** 620 -> "620", 1240 -> "1.2k", 15300 -> "15k", 1.5M -> "1.5M". */
    static String fmt(float v) {
        long n = Math.round(v);
        if (n < 1000) return String.valueOf(n);
        if (n < 10000) return trim(n / 1000.0) + "k";
        if (n < 1000000) return (n / 1000) + "k";
        if (n < 10000000) return trim(n / 1000000.0) + "M";
        return (n / 1000000) + "M";
    }

    private static String trim(double d) {
        String s = String.format(java.util.Locale.ROOT, "%.1f", Math.floor(d * 10) / 10.0);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    // ------------------------------------------------------------------------------------------ icons (centred on 0,0)

    private static void crystal(Matrix4f m) {
        poly(m, argb(90, 143, 224, 255), 0f, -6.5f, 4.2f, -1f, 0f, 6.5f, -4.2f, -1f);
        poly(m, argb(168, 208, 255, 255), 0f, -6.5f, 4.2f, -1f, 0f, 0.5f, -1.6f, -2f);
        poly(m, argb(46, 86, 168, 255), 0f, 6.5f, -4.2f, -1f, 0f, 0.5f);
    }

    private static void chestplate(Matrix4f m) {
        float x = 0f, y = 0f;
        int s1 = argb(188, 214, 255, 255), s2 = argb(70, 100, 156, 255);
        polyColors(m, 0f, new float[] {x - 3.5f, y - 3.9f, x + 3.5f, y - 3.9f, x + 3.0f, y + 4.2f, x - 3.0f, y + 4.2f}, new int[] {s1, s1, s2, s2});
        poly(m, withAlpha(WHITE, 70), x - 3.5f, y - 3.9f, x, y - 3.9f, x, y + 4.2f, x - 3.0f, y + 4.2f);
        poly(m, withAlpha(INK, 80), x, y - 3.9f, x + 3.5f, y - 3.9f, x + 3.0f, y + 4.2f, x, y + 4.2f);
        int g1 = argb(248, 218, 130, 255), g2 = argb(168, 120, 46, 255);
        polyColors(m, 0f, new float[] {x - 5.2f, y - 4.1f, x - 2.5f, y - 4.1f, x - 2.5f, y - 1.1f, x - 5.2f, y - 1.9f}, new int[] {g1, g1, g2, g2});
        polyColors(m, 0f, new float[] {x + 2.5f, y - 4.1f, x + 5.2f, y - 4.1f, x + 5.2f, y - 1.9f, x + 2.5f, y - 1.1f}, new int[] {g1, g1, g2, g2});
        poly(m, INK, x - 1.5f, y - 4.0f, x + 1.5f, y - 4.0f, x, y - 2.2f);
        line(m, withAlpha(GOLD, 240), x - 3.1f, y + 1.7f, x + 3.1f, y + 1.7f, 0.7f);
    }

    private static void drumstick(Matrix4f m) {
        float x = 0f, y = 0f;
        line(m, BONE, x + 0.8f, y + 0.8f, x + 4.1f, y + 4.1f, 1.5f);
        ellipse(m, BONE, x + 3.8f, y + 4.9f, 1.0f, 1.0f);
        ellipse(m, BONE, x + 4.9f, y + 3.8f, 1.0f, 1.0f);
        ellipse(m, argb(96, 44, 18, 255), x - 1.2f, y - 1.2f, 4.2f, 3.8f);
        ellipse(m, argb(150, 74, 28, 255), x - 1.2f, y - 1.2f, 3.6f, 3.2f);
        ellipse(m, argb(236, 148, 62, 255), x - 1.6f, y - 1.6f, 2.9f, 2.5f);
        ellipse(m, argb(255, 196, 108, 255), x - 2.2f, y - 2.2f, 1.6f, 1.2f);
    }
}

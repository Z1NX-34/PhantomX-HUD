package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Diamond slots around the crest (drawn in the player block's pose):
 * the Epic Fight special move centred on the crest's bottom-left edge (always there, a default move icon when there is
 * none), and a row of three refill diamonds on the crest's top-right: mana, armor, hunger. Each fills from the bottom
 * with its colour, its icon stays fully visible, and current / max is written beside it. Big numbers shorten (1.2k,
 * 15k, 1.5M).
 */
final class GaugeSlots {
    private GaugeSlots() {}

    private static final float HS_SPECIAL = 19.2f * 0.8f;
    private static final float HS_MANA = 11.7f * 0.95f * 0.95f, HS_SMALL = 11.7f * 0.8f * 0.9f;
    private static final float GAP = 2.4f;

    static void render(GuiGraphics g, Font font, Player p, float partial) {
        if (HudConfig.SHOW_SKILLS.get()) special(g, partial);
        if (HudConfig.SHOW_CHIPS.get()) refillRow(g, font, p);
    }

    // ------------------------------------------------------------------------------------------ special move

    private static void special(GuiGraphics g, float partial) {
        float cx = PlayerBlock.CX, cy = PlayerBlock.CY, rg = PlayerBlock.R + 3f;
        // centred on the crest's bottom-left edge, with a small gap
        float hs = HS_SPECIAL;
        float dd = (cx - cy) - rg - hs - 3.2f, ss = cx + cy;
        float x = (ss + dd) / 2f, y = (ss - dd) / 2f;

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
        float bottom = cy - 1.5f;                          // lowest pixel stays above the crest's middle line

        float ym = bottom - HS_MANA;
        float x = cx + rg - Math.abs(ym - cy) + HS_MANA + GAP + 0.2f;
        float[] mana = ResourceSources.mana();
        x = refill(g, font, x, ym, HS_MANA, 0, mana == null ? 0f : mana[0] / mana[1], argb(74, 134, 232, 255), argb(34, 70, 150, 255),
                mana == null ? "-" : fmt(mana[0]), mana == null ? "" : "/" + fmt(mana[1]), mana != null);

        float ys = bottom - HS_SMALL;
        float[] ar = armor(p);
        x += HS_SMALL;
        x = refill(g, font, x, ys, HS_SMALL, 1, ar[0] / ar[1], argb(143, 180, 236, 255), argb(70, 96, 150, 255),
                fmt(ar[0]), "/" + fmt(ar[1]), true);

        x += HS_SMALL;
        int food = p.getFoodData().getFoodLevel();
        refill(g, font, x, ys, HS_SMALL, 2, food / 20f, argb(236, 148, 62, 255), argb(150, 80, 30, 255),
                fmt(food), "/20", true);
    }

    /**
     * {armor, full-set armor}. With all four pieces on, the gauge is full. With pieces missing, the full value is
     * estimated from the pieces you do wear, so an empty slot shows up as a gap instead of a made-up cap.
     */
    private static float[] armor(Player p) {
        float cur = p.getArmorValue();
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        int have = 0;
        double sum = 0;
        for (EquipmentSlot s : slots) {
            ItemStack st = p.getItemBySlot(s);
            if (st.isEmpty()) continue;
            have++;
            for (AttributeModifier mod : st.getAttributeModifiers(s).get(Attributes.ARMOR)) {
                if (mod.getOperation() == AttributeModifier.Operation.ADDITION) sum += mod.getAmount();
            }
        }
        float max = cur;
        if (have == 0) max = Math.max(cur, 20f);
        else if (have < 4) max = cur + (float) (sum / have * (4 - have));
        return new float[] {cur, Math.max(1f, max)};
    }

    /**
     * kind: 0 mana crystal, 1 chestplate, 2 drumstick. Draws the diamond with its numbers to the right and returns the
     * x of the diamond centre the next one should use (without its own half-size).
     */
    private static float refill(GuiGraphics g, Font font, float x, float y, float hs, int kind, float frac, int col, int dark,
            String cur, String max, boolean live) {
        Matrix4f m = g.pose().last().pose();
        frac = PlayerBlock.clamp01(frac);
        diamondFill(m, withAlpha(INK, 240), x, y, hs, hs);
        diamondFill(m, withAlpha(argb(26, 28, 52, 255), 235), x, y, hs - 1f, hs - 1f);

        // liquid, rising from the bottom vertex
        if (live && frac > 0.004f) {
            float h = hs - 1f, ly = h - 2f * h * frac;       // surface height relative to the centre
            float[] xy;
            if (ly >= 0f) xy = new float[] {x - (h - ly), y + ly, x + (h - ly), y + ly, x, y + h};
            else xy = new float[] {x - (h + ly), y + ly, x + (h + ly), y + ly, x + h, y, x, y + h, x - h, y};
            int top = withAlpha(col, 165), bot = withAlpha(dark, 200);
            int[] cols = xy.length == 6 ? new int[] {top, top, bot} : new int[] {top, top, top, bot, top};
            polyColors(m, 0f, xy, cols);
            float sw = ly >= 0f ? h - ly : h + ly;
            line(m, withAlpha(WHITE, 150), x - sw + 0.6f, y + ly, x + sw - 0.6f, y + ly, 0.6f);
        }

        // icon on top of the liquid, fully visible
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        float is = hs / 8.6f * 0.82f;
        g.pose().scale(is, is, 1f);
        Matrix4f mi = g.pose().last().pose();
        if (kind == 0) crystal(mi); else if (kind == 1) chestplate(mi); else drumstick(mi);
        g.pose().popPose();

        m = g.pose().last().pose();
        diamondLine(m, live ? GOLD : DGOLD, x, y, hs, hs, 1.1f);
        diamondLine(m, withAlpha(PURP, 200), x, y, hs - 1.9f, hs - 1.9f, 0.45f);

        // numbers beside the diamond: current in white, max small and dim after it
        float tcur = 0.7f, tmax = 0.5f;
        float wc = font.width(cur) * tcur, wm = max.isEmpty() ? 0f : font.width(max) * tmax;
        float tx = x + hs + 1.8f, ty = y - 3.5f * tcur + 0.6f;
        g.pose().pushPose();
        g.pose().translate(tx, ty, 0f);
        g.pose().scale(tcur, tcur, 1f);
        g.drawString(font, cur, 0, 0, live ? WHITE : argb(150, 150, 170, 255), true);
        g.pose().popPose();
        if (wm > 0f) {
            g.pose().pushPose();
            g.pose().translate(tx + wc + 0.4f, ty + 7f * tcur - 7f * tmax, 0f);
            g.pose().scale(tmax, tmax, 1f);
            g.drawString(font, max, 0, 0, argb(214, 206, 188, 255), true);
            g.pose().popPose();
        }
        return tx + wc + (wm > 0f ? wm + 0.4f : 0f) + 3.4f + GAP * 0f;
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

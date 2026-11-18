package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
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
    static final float HS_MANA = 11.7f * 0.95f * 0.95f;
    private static final float HS_SMALL = 11.7f * 0.8f * 0.9f * 0.85f;
    private static final ResourceLocation TEX_MANA = new ResourceLocation(PhantomXHud.MODID, "textures/gui/mana.png");
    private static final ResourceLocation TEX_MOVE = new ResourceLocation(PhantomXHud.MODID, "textures/gui/default_move.png");

    /** Top y of the refill row (relative to the block origin), used to stack the mount bar above it. */
    static float rowTop() {
        return PlayerBlock.CY - 1.5f - 2f * HS_MANA;
    }
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
            icon(g, TEX_MOVE, x, y, hs * 1.45f, 0.8f);
        }
        m = g.pose().last().pose();
        diamondLine(m, GOLD, x, y, hs, hs, 1.3f);
        diamondLine(m, withAlpha(PURP, 190), x, y, hs - 2.6f, hs - 2.6f, 0.55f);
    }

    // ------------------------------------------------------------------------------------------ refill diamonds

    private static void refillRow(GuiGraphics g, Font font, Player p) {
        float cx = PlayerBlock.CX, cy = PlayerBlock.CY, rg = PlayerBlock.R + 3f;
        float bottom = cy - 1.5f;                          // lowest pixel stays above the crest's middle line

        float ys = bottom - HS_SMALL;
        float[] ar = armor(p);
        float x = cx + rg - Math.abs(ys - cy) + GAP + 0.2f + HS_SMALL;       // where the mana diamond used to start
        x = refill(g, font, x, ys, HS_SMALL, 1, ar[0] / ar[1], argb(88, 104, 138, 255), argb(44, 54, 84, 255),
                fmt(ar[0]), "/" + fmt(ar[1]), true);

        x += HS_SMALL;
        int food = p.getFoodData().getFoodLevel();
        refill(g, font, x, ys, HS_SMALL, 2, food / 20f, argb(150, 92, 44, 255), argb(92, 50, 24, 255),
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
            int top = withAlpha(col, 120), bot = withAlpha(dark, 170);
            int[] cols = xy.length == 6 ? new int[] {top, top, bot} : new int[] {top, top, top, bot, top};
            polyColors(m, 0f, xy, cols);
            float sw = ly >= 0f ? h - ly : h + ly;
            line(m, withAlpha(WHITE, 70), x - sw + 0.6f, y + ly, x + sw - 0.6f, y + ly, 0.5f);
        }

        // ornament first, icon on top of it so the lines never cut through the icon
        m = g.pose().last().pose();
        diamondLine(m, live ? GOLD : DGOLD, x, y, hs, hs, 1.1f);
        diamondLine(m, withAlpha(PURP, 200), x, y, hs - 1.9f, hs - 1.9f, 0.45f);
        if (kind == 0) icon(g, TEX_MANA, x, y, hs * 1.42f, live ? 1f : 0.4f);
        else if (kind == 1) vanillaIcon(g, 34, 9, -1, -1, x, y, hs * 1.5f, 1f, 0.55f, 0.8f, 1f);       // vanilla armor icon, cooler sheen
        else vanillaIcon(g, 52, 27, 16, 27, x, y, hs * 1.5f, 1f, 1f, 0.6f, 0.2f);                     // vanilla food icon, warmer sheen

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
}

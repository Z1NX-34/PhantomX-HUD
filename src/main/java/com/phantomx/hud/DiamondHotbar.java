package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Bottom-right module. A big diamond shows the item in your hand (it follows the scroll wheel / number keys). Three
 * clumps of four small diamonds, each clump exactly the size of the big diamond, wrap its right half:
 * top-right = hotbar 1-4, right = hotbar 5-8, bottom-right = hotbar 9, off-hand, selected spell, healing flask (reserved).
 * The XP bar runs along the whole top-left and bottom-left edge of the module with the level badge at its apex.
 * Slots inside a clump are ordered top, right, bottom, left.
 */
final class DiamondHotbar {
    private DiamondHotbar() {}

    private static final float B = 21f, GP = 1.6f, AA = B + GP, HS = (B - 1.2f) / 2f;
    private static final float EXT_R = 3f * B + 2f * GP, EXT_B = AA + B + 6.5f;
    private static final int XP_TOP = argb(120, 238, 152, 255), XP_BOT = argb(32, 130, 84, 255);
    private static final int SEL = argb(255, 226, 140, 255);

    private static int lastSel = -1;
    private static long changeAt;

    /** Centre of the big slot in the symmetric row: the mirror image of the player crest's centre. */
    static float[] rowCenter(int sw, int sh, float scale) {
        return new float[] {sw - (HudConfig.MARGIN_X.get() + scale * PlayerBlock.CX), (sh - HudConfig.MARGIN_Y.get()) + scale * PlayerBlock.CY};
    }

    static void render(GuiGraphics g, Font font, Player p, int sw, int sh, float scale, float partial) {
        float ms = scale * HudConfig.MODULE_SCALE.get().floatValue();
        boolean row = Layout.itemsStyle() == Layout.ItemsStyle.ROW;
        float cx, cy;
        if (row) {
            float[] c = rowCenter(sw, sh, scale);
            cx = c[0];
            cy = c[1];
        } else {
            cx = sw - ms * (HudConfig.MODULE_MARGIN_X.get() + EXT_R);
            cy = sh - ms * (HudConfig.MODULE_MARGIN_Y.get() + EXT_B);
        }

        int sel = p.getInventory().selected;
        long now = System.currentTimeMillis();
        if (lastSel != sel) {
            if (lastSel >= 0) changeAt = now;
            lastSel = sel;
        }

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(ms, ms, 1f);
        if (HudConfig.SHOW_HOTBAR.get()) {
            if (row) {
                float st = 2f * B + 2f * GP;
                clump(g, font, p, -st, 0f, partial, sel, 0);
                clump(g, font, p, -2f * st, 0f, partial, sel, 4);
                clump(g, font, p, -3f * st, 0f, partial, sel, 8);
            } else {
                clump(g, font, p, AA, -AA, partial, sel, 0);
                clump(g, font, p, 2f * B + 2f * GP, 0f, partial, sel, 4);
                clump(g, font, p, AA, AA, partial, sel, 8);
            }
            mainDiamond(g, font, p, now);
        }
        if (HudConfig.SHOW_XP.get()) {
            if (row) xpBar(g, font, p, now);
            else xp(g, font, p, now);
        }
        g.pose().popPose();
    }

    // ------------------------------------------------------------------------------------------ big diamond

    private static void mainDiamond(GuiGraphics g, Font font, Player p, long now) {
        ItemStack stack = p.getMainHandItem();
        Matrix4f m = g.pose().last().pose();
        plate(m, 0f, 0f, B, true);

        float t = Math.min(1f, (now - changeAt) / 190f);
        float ease = 1f - (1f - t) * (1f - t);
        if (!stack.isEmpty()) {
            float k = 1.55f * (0.78f + 0.22f * ease);
            beginDiamondClip(g, 0f, 0f, B - 1.6f, B + 20f);
            g.pose().pushPose();
            g.pose().scale(k, k, 1f);
            g.renderItem(p, stack, -8, -8, 7);
            g.pose().popPose();
            endDiamondClip(g);
            if (stack.getCount() > 1) corner(g, font, String.valueOf(stack.getCount()), 5.5f, 8.2f, 0.85f);
            if (stack.isBarVisible()) {
                float frac = stack.getBarWidth() / 13f;
                Matrix4f mm = g.pose().last().pose();
                rect(mm, withAlpha(INK, 235), -7.5f, 11.2f, 7.5f, 12.8f);
                rect(mm, stack.getBarColor() | 0xFF000000, -7.5f, 11.2f, -7.5f + 15f * frac, 12.8f);
            }
        } else {
            Matrix4f mm = g.pose().last().pose();
            diamondFill(mm, withAlpha(GOLD, 55), 0f, 0f, 3f, 3f);
        }
        m = g.pose().last().pose();
        diamondLine(m, GOLD, 0f, 0f, B, B, 1.5f);
        diamondLine(m, withAlpha(PURP, 230), 0f, 0f, B - 4f, B - 4f, 0.8f);
        diamondLine(m, withAlpha(GOLD, 100), 0f, 0f, B - 6.5f, B - 6.5f, 0.5f);
        if (t < 1f) diamondLine(m, withAlpha(SEL, Math.round(200 * (1f - t))), 0f, 0f, B + 1f + 2.5f * t, B + 1f + 2.5f * t, 1.1f);
    }

    // ------------------------------------------------------------------------------------------ clumps and slots

    private static void clump(GuiGraphics g, Font font, Player p, float cx, float cy, float partial, int sel, int first) {
        float s = HS + 1.2f;
        float[][] pos = {{0f, -s}, {s, 0f}, {0f, s}, {-s, 0f}};
        for (int i = 0; i < 4; i++) {
            float x = cx + pos[i][0], y = cy + pos[i][1];
            int idx = first + i;
            if (idx <= 8) {
                ItemStack st = p.getInventory().items.get(idx);
                itemSlot(g, font, p, st, x, y, idx == sel, partial, idx == 8 ? 3 : -1);
            } else if (idx == 9) {
                itemSlot(g, font, p, p.getOffhandItem(), x, y, false, partial, 1);
            } else if (idx == 10) {
                spellSlot(g, x, y);
            } else {
                emptySlot(g, x, y, 2);
            }
        }
    }

    /** glyph: shown dim while the slot is empty. -1 none (a small dim diamond), 0 sword, 1 shield, 2 flask, 3 star. */
    private static void itemSlot(GuiGraphics g, Font font, Player p, ItemStack st, float x, float y, boolean selected, float partial, int glyph) {
        Matrix4f m = g.pose().last().pose();
        plate(m, x, y, HS, selected);
        if (!st.isEmpty()) {
            beginDiamondClip(g, x, y, HS - 1f, HS + 14f);
            g.pose().pushPose();
            g.pose().translate(x, y, 0f);
            g.pose().scale(0.8f, 0.8f, 1f);
            g.renderItem(p, st, -8, -8, 3);
            g.pose().popPose();
            endDiamondClip(g);
            if (st.getCount() > 1) corner(g, font, String.valueOf(st.getCount()), x + HS * 0.34f, y + HS * 0.58f, 0.55f);
            float cd = p.getCooldowns().getCooldownPercent(st.getItem(), partial);
            if (cd > 0f) diamondFill(g.pose().last().pose(), withAlpha(INK, 165), x, y, (HS - 1f) * cd, (HS - 1f) * cd);
        } else if (glyph >= 0) {
            glyph(g.pose().last().pose(), x, y, glyph, HS / 12.5f);
        } else {
            diamondFill(m, argb(42, 40, 64, 255), x, y, HS * 0.22f, HS * 0.22f);
        }
        frame(g.pose().last().pose(), x, y, HS, selected);
    }

    private static void spellSlot(GuiGraphics g, float x, float y) {
        Matrix4f m = g.pose().last().pose();
        plate(m, x, y, HS, false);
        IronsSpells.Entry sel = null;
        if (IronsSpells.loaded()) {
            for (IronsSpells.Entry e : IronsSpells.window(5)) if (e.selected) sel = e;
        }
        if (sel != null) {
            beginDiamondClip(g, x, y, HS - 1f, HS + 14f);
            int px = Math.round((HS - 1f) * 1.55f);
            g.blit(sel.icon, Math.round(x - px / 2f), Math.round(y - px / 2f), 0, 0, px, px, px, px);
            endDiamondClip(g);
            if (sel.cooldown > 0f) {
                float r = (HS - 1f) * sel.cooldown;
                diamondFill(g.pose().last().pose(), withAlpha(INK, 165), x, y, r, r);
            }
        } else {
            glyph(m, x, y, 3, HS / 12.5f);
        }
        frame(g.pose().last().pose(), x, y, HS, false);
    }

    /** The reserved healing-flask slot: empty, with a flask icon. */
    private static void emptySlot(GuiGraphics g, float x, float y, int glyph) {
        Matrix4f m = g.pose().last().pose();
        plate(m, x, y, HS, false);
        glyph(m, x, y, glyph, HS / 12.5f);
        frame(g.pose().last().pose(), x, y, HS, false);
    }

    private static void plate(Matrix4f m, float x, float y, float hs, boolean active) {
        diamondFill(m, withAlpha(INK, 240), x, y, hs, hs);
        float hi = hs - 1.4f;
        int centre = withAlpha(argb(78, 66, 150, 255), active ? 240 : 170);
        int rim = withAlpha(argb(20, 18, 46, 255), 238);
        polyColors(m, 0f, new float[] {x, y, x, y - hi, x + hi, y, x, y + hi, x - hi, y, x, y - hi}, new int[] {centre, rim, rim, rim, rim, rim});
    }

    private static void frame(Matrix4f m, float x, float y, float hs, boolean selected) {
        if (selected) {
            diamondFill(m, withAlpha(GOLD, 40), x, y, hs - 1f, hs - 1f);
            diamondLine(m, withAlpha(SEL, 90), x, y, hs + 1.3f, hs + 1.3f, 0.8f);
            diamondLine(m, SEL, x, y, hs, hs, 1.3f);
        } else {
            diamondLine(m, GOLD, x, y, hs, hs, 0.85f);
        }
        diamondLine(m, withAlpha(PURP, 205), x, y, hs - 1.8f, hs - 1.8f, 0.4f);
    }

    private static void corner(GuiGraphics g, Font font, String t, float x, float y, float ts) {
        g.pose().pushPose();
        g.pose().translate(x, y - 3.5f * ts, 0f);
        g.pose().scale(ts, ts, 1f);
        g.drawString(font, t, Math.round(-font.width(t) / 2f), 0, WHITE, true);
        g.pose().popPose();
    }

    private static void glyph(Matrix4f m, float x, float y, int glyph, float s) {
        int c = withAlpha(GOLD, 95);
        switch (glyph) {
            case 0 -> {
                line(m, c, x - 4f * s, y + 4f * s, x + 4f * s, y - 4f * s, 1.1f * s);
                line(m, c, x - 3.2f * s, y - 0.8f * s, x + 0.8f * s, y + 3.2f * s, 1.1f * s);
            }
            case 1 -> poly(m, c, x - 3.6f * s, y - 3.8f * s, x + 3.6f * s, y - 3.8f * s, x + 3.6f * s, y + 0.4f * s, x, y + 4.4f * s, x - 3.6f * s, y + 0.4f * s);
            case 2 -> {
                poly(m, c, x - 1.1f * s, y - 4f * s, x + 1.1f * s, y - 4f * s, x + 1.1f * s, y - 1.4f * s, x - 1.1f * s, y - 1.4f * s);
                poly(m, c, x - 1.4f * s, y - 1.6f * s, x + 1.4f * s, y - 1.6f * s, x + 3.4f * s, y + 3.6f * s, x - 3.4f * s, y + 3.6f * s);
                line(m, argb(20, 18, 46, 255), x - 1.4f * s, y + 1.4f * s, x + 1.4f * s, y + 1.4f * s, 0.8f * s);
                line(m, argb(20, 18, 46, 255), x, y + 0.1f * s, x, y + 2.7f * s, 0.8f * s);
            }
            default -> poly(m, c, x, y - 4.6f * s, x + 1.2f * s, y - 1.2f * s, x + 4.6f * s, y, x + 1.2f * s, y + 1.2f * s, x, y + 4.6f * s,
                    x - 1.2f * s, y + 1.2f * s, x - 4.6f * s, y, x - 1.2f * s, y - 1.2f * s);
        }
    }

    // ------------------------------------------------------------------------------------------ xp

    /**
     * Seven slanted segments per side along the module's top-left and bottom-left edges, filling from the bottom end
     * through the level badge to the top end. A dot sits at each end.
     */
    private static void xp(GuiGraphics g, Font font, Player p, long now) {
        Matrix4f m = g.pose().last().pose();
        final int per = 7;
        final float off = 8.5f, d0 = 5f, d1 = 66f, lg = 1.1f, wd = 3.4f, r2 = 0.70710678f;
        float ax = -B - off, ay = 0f;
        float ls = (d1 - d0 - (per - 1) * lg) / per;
        float prog = PlayerBlock.clamp01(p.experienceProgress) * per * 2f;
        float wave = (now % 3200L) / 3200f * (per * 2 + 4) - 2f;
        for (int k = 0; k < per * 2; k++) {
            boolean top = k >= per;
            int idx = top ? k - per : per - 1 - k;
            float t0 = d0 + idx * (ls + lg), t1 = t0 + ls;
            float ux = r2, uy = top ? -r2 : r2, nx = -uy, ny = ux;
            float[] edge = band(ax, ay, ux, uy, nx, ny, t0 - 0.6f, t1 + 0.6f, wd / 2f + 0.6f);
            poly(m, withAlpha(GOLD, 95), edge);
            poly(m, withAlpha(INK, 240), band(ax, ay, ux, uy, nx, ny, t0, t1, wd / 2f));
            float f = PlayerBlock.clamp01(prog - k);
            if (f <= 0.001f) continue;
            // top arm fills from the apex outwards; bottom arm from its outer end towards the apex (one continuous line)
            float ta = top ? t0 : t1 - (t1 - t0) * f, tb = top ? t0 + (t1 - t0) * f : t1;
            float te = top ? tb : ta, h2 = wd / 2f - 0.4f;
            float[] q = band(ax, ay, ux, uy, nx, ny, ta, tb, h2);
            polyColors(m, 0f, q, new int[] {XP_TOP, XP_TOP, XP_BOT, XP_BOT});
            float kk = Math.max(0f, 1f - Math.abs(k - wave) / 1.8f);
            if (kk > 0f) poly(m, withAlpha(WHITE, Math.round(70 * kk)), q);
            if (f < 0.999f) {
                float[] a = {ax + ux * te + nx * (-h2), ay + uy * te + ny * (-h2)};
                float[] b = {ax + ux * te + nx * h2, ay + uy * te + ny * h2};
                line(m, withAlpha(WHITE, 235), a[0], a[1], b[0], b[1], 0.9f);
            }
        }
        for (int sg = -1; sg <= 1; sg += 2) {
            float t = d1 + 4f;
            float x = ax + r2 * t, y = ay + sg * r2 * t;
            diamondFill(m, INK, x, y, 2.7f, 2.7f);
            diamondFill(m, GOLD, x, y, 1.8f, 1.8f);
        }

        // level badge at the apex
        float bx = ax - 3f, hw = 7f;
        poly(m, GOLD, roundedDiamond(bx, ay, hw + 1.2f, hw + 1.2f, 3.8f));
        poly(m, INK, roundedDiamond(bx, ay, hw, hw, 3.4f));
        poly(m, withAlpha(XP_TOP, 150), roundedDiamond(bx, ay, hw - 0.9f, hw - 0.9f, 3.1f));
        poly(m, argb(20, 44, 36, 255), roundedDiamond(bx, ay, hw - 1.7f, hw - 1.7f, 2.7f));
        String lvl = String.valueOf(Math.min(999, p.experienceLevel));
        float ts = lvl.length() >= 3 ? 0.58f : lvl.length() == 2 ? 0.78f : 0.95f;
        g.pose().pushPose();
        g.pose().translate(bx, ay - 3.5f * ts, 0f);
        g.pose().scale(ts, ts, 1f);
        g.drawString(font, lvl, Math.round(-font.width(lvl) / 2f), 0, BONE, true);
        g.pose().popPose();
    }

    /**
     * Symmetric row: one long slanted bar under the whole row, 14 segments, filling from the big slot outwards to the left,
     * with the level badge on its left end.
     */
    private static void xpBar(GuiGraphics g, Font font, Player p, long now) {
        Matrix4f m = g.pose().last().pose();
        final int n = 14;
        float st = 2f * B + 2f * GP;
        float y0 = B + 5.6f, h = 4.6f, y1 = y0 + h, ins = 0.9f, gap = 1.1f;
        float xr = B + 1f, xl = -(3f * st + B) - 1f, len = xr - xl;
        float sl = (len - (n - 1) * gap) / n;
        float prog = PlayerBlock.clamp01(p.experienceProgress) * n;
        float wave = (now % 3200L) / 3200f * (n + 4) - 2f;
        for (int i = 0; i < n; i++) {
            float x1 = xr - i * (sl + gap), x0 = x1 - sl;
            poly(m, withAlpha(GOLD, 95), lean(x0 - 0.6f, x1 + 0.6f, y0, y0 - 0.6f, y1 + 0.6f));
            poly(m, withAlpha(INK, 240), lean(x0, x1, y0, y0, y1));
            float f = PlayerBlock.clamp01(prog - i);
            if (f <= 0.001f) continue;
            float xa = x1 - sl * f;
            float[] q = lean(xa, x1, y0, y0 + ins, y1 - ins);
            polyColors(m, 0f, q, new int[] {XP_TOP, XP_TOP, XP_BOT, XP_BOT});
            float kk = Math.max(0f, 1f - Math.abs(i - wave) / 1.8f);
            if (kk > 0f) poly(m, withAlpha(WHITE, Math.round(70 * kk)), q);
            if (f < 0.999f) line(m, withAlpha(WHITE, 235), q[0], q[1], q[6], q[7], 0.9f);
        }
        float bx = xl - 8f, by = (y0 + y1) / 2f, hw = 7f;
        poly(m, GOLD, roundedDiamond(bx, by, hw + 1.2f, hw + 1.2f, 3.8f));
        poly(m, INK, roundedDiamond(bx, by, hw, hw, 3.4f));
        poly(m, withAlpha(XP_TOP, 150), roundedDiamond(bx, by, hw - 0.9f, hw - 0.9f, 3.1f));
        poly(m, argb(20, 44, 36, 255), roundedDiamond(bx, by, hw - 1.7f, hw - 1.7f, 2.7f));
        String lvl = String.valueOf(Math.min(999, p.experienceLevel));
        float ts = lvl.length() >= 3 ? 0.58f : lvl.length() == 2 ? 0.78f : 0.95f;
        g.pose().pushPose();
        g.pose().translate(bx, by - 3.5f * ts, 0f);
        g.pose().scale(ts, ts, 1f);
        g.drawString(font, lvl, Math.round(-font.width(lvl) / 2f), 0, BONE, true);
        g.pose().popPose();
    }

    /** Parallelogram between xl..xr at the top edge y0, leaning down-left, covering ya..yb. */
    private static float[] lean(float xl, float xr, float y0, float ya, float yb) {
        return new float[] {xl - (ya - y0), ya, xr - (ya - y0), ya, xr - (yb - y0), yb, xl - (yb - y0), yb};
    }

    /** Rectangle along the arm from t0 to t1 with the given half width. */
    private static float[] band(float ax, float ay, float ux, float uy, float nx, float ny, float t0, float t1, float hw) {
        return new float[] {
            ax + ux * t0 + nx * -hw, ay + uy * t0 + ny * -hw,
            ax + ux * t1 + nx * -hw, ay + uy * t1 + ny * -hw,
            ax + ux * t1 + nx * hw, ay + uy * t1 + ny * hw,
            ax + ux * t0 + nx * hw, ay + uy * t0 + ny * hw};
    }
}

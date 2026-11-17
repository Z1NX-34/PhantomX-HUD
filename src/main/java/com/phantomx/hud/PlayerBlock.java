package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

/** Bottom-left: diamond crest (live model clipped to it), health and stamina bars on its lower right, special-move slot and refill diamonds. */
final class PlayerBlock {
    private PlayerBlock() {}

    static final float R = 30f;                       // crest half-diagonal
    private static final float HEALTH_LEN = 138f, STAMINA_LEN = 76f;
    private static final float HEALTH_H = 7.5f, STAMINA_H = 6f;

    /** Crest centre relative to the block origin (bottom-left anchor). */
    static final float CX = 46f, CY = -(R + 14f);
    /** The small skill diamonds sit under the crest, right of the special slot (local coordinates of the block). */
    static final float SKILL_X = 60f, SKILL_CY = -5f;

    private static float blockTx, blockTy, blockScale = 1f;
    private static float trailHealth = -1f;
    private static long lastNanos;

    static void render(GuiGraphics g, Player p, int sw, int sh, float scale, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;

        float tx = HudConfig.MARGIN_X.get(), ty = sh - HudConfig.MARGIN_Y.get();
        // the model is drawn in absolute screen coordinates, so it needs the layout transform applied by hand
        blockTx = Layout.mapX(Layout.Mod.PLAYER, sw, sh, tx);
        blockTy = Layout.mapY(Layout.Mod.PLAYER, sw, sh, ty);
        blockScale = scale * Layout.scaleOf(Layout.Mod.PLAYER);

        g.pose().pushPose();
        g.pose().translate(tx, ty, 0);
        g.pose().scale(scale, scale, 1f);
        drawCrest(g, p);
        drawBars(g, font, p, dt);
        GaugeSlots.render(g, font, p, partial);
        g.pose().popPose();
    }

    /** Where the top of the crest is on screen (used to stack the skill row above it). */
    static float crestTopScreenY(int sh, float scale) {
        return (sh - HudConfig.MARGIN_Y.get()) + scale * (CY - R - 3f);
    }

    // ------------------------------------------------------------------------------------------ crest

    /**
     * Layering: the two upper slopes are behind the model (the head can rise past them), the model is in front of those,
     * and the two lower slopes are drawn over the model and clip it, so the body is enclosed by the lower half.
     */
    private static void drawCrest(GuiGraphics g, Player p) {
        Matrix4f m = g.pose().last().pose();
        // upper half: behind the model
        diamondHalf(m, withAlpha(GOLD, 120), CX, CY, R + 3f, R + 3f, 0.6f, true);
        diamondHalf(m, GOLD, CX, CY, R, R, 1.5f, true);
        diamondHalf(m, withAlpha(PURP, 210), CX, CY, R - 4f, R - 4f, 0.8f, true);
        diamondHalf(m, withAlpha(GOLD, 90), CX, CY, R - 6.5f, R - 6.5f, 0.5f, true);
        diamondFill(m, INK, CX, CY - R - 3f, 2.8f, 2.8f);
        diamondFill(m, GOLD, CX, CY - R - 3f, 2f, 2f);

        drawModel(g, p);

        // lower half: in front, enclosing the lower body
        m = g.pose().last().pose();
        diamondHalf(m, withAlpha(GOLD, 120), CX, CY, R + 3f, R + 3f, 0.6f, false);
        diamondHalf(m, GOLD, CX, CY, R, R, 1.5f, false);
        diamondHalf(m, withAlpha(PURP, 210), CX, CY, R - 4f, R - 4f, 0.8f, false);
        diamondHalf(m, withAlpha(GOLD, 90), CX, CY, R - 6.5f, R - 6.5f, 0.5f, false);
        float[][] studs = {{CX, CY + R + 3f}};
        for (float[] s : studs) {
            diamondFill(m, INK, s[0], s[1], 2.8f, 2.8f);
            diamondFill(m, GOLD, s[0], s[1], 2f, 2f);
        }
    }

    /**
     * The model is drawn with a depth mask over the part of the lower half that lies outside the diamond, so nothing of
     * it can leave the lower slopes. Above the middle line nothing is masked. No stencil buffer is needed.
     */
    private static void drawModel(GuiGraphics g, Player p) {
        if (!(p instanceof AbstractClientPlayer acp)) return;
        Matrix4f m = g.pose().last().pose();
        float r = R - 0.6f, B = R * 3.5f, Z = 300f;

        g.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.colorMask(false, false, false, false);
        try {
            // lower-left and lower-right regions outside the diamond (both convex); above the middle line nothing is masked
            polyZ(m, WHITE, Z, CX - B, CY, CX - r, CY, CX, CY + r, CX, CY + B, CX - B, CY + B);
            polyZ(m, WHITE, Z, CX + B, CY, CX + r, CY, CX, CY + r, CX, CY + B, CX + B, CY + B);
        } finally {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        }

        g.pose().pushPose();
        g.pose().setIdentity();
        try {
            float ax = blockTx + blockScale * CX, ay = blockTy + blockScale * CY, ar = blockScale * R;
            float scale = blockScale * 28f * 1.45f;               // same pixel size as the old 28-radius crest
            float feetY = ay - ar * 1.0f + scale * 1.8f;          // head top reaches the top vertex height and may rise past the upper frame
            drawVanillaModel(g, acp, Math.round(ax), Math.round(feetY), Math.round(scale));
        } finally {
            g.pose().popPose();
            g.flush();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        }
    }

    private static void drawVanillaModel(GuiGraphics g, AbstractClientPlayer p, int x, int y, int scale) {
        float bodyRot = p.yBodyRot, bodyRotO = p.yBodyRotO, headRot = p.yHeadRot, headRotO = p.yHeadRotO;
        float yRot = p.getYRot(), yRotO = p.yRotO, xRot = p.getXRot(), xRotO = p.xRotO;
        try {
            float face = 180f + 22f;
            p.yBodyRot = face;
            p.yBodyRotO = face;
            p.yHeadRot = face;
            p.yHeadRotO = face;
            p.setYRot(face);
            p.yRotO = face;
            p.setXRot(0f);
            p.xRotO = 0f;
            Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
            InventoryScreen.renderEntityInInventory(g, x, y, scale, pose, null, p);
        } finally {
            p.yBodyRot = bodyRot;
            p.yBodyRotO = bodyRotO;
            p.yHeadRot = headRot;
            p.yHeadRotO = headRotO;
            p.setYRot(yRot);
            p.yRotO = yRotO;
            p.setXRot(xRot);
            p.xRotO = xRotO;
        }
    }

    // ------------------------------------------------------------------------------------------ bars

    private static final float GAP = 6.5f;

    private static final float HEALTH_Y = 3f;                                       // relative to the crest centre
    private static final float STAMINA_Y = HEALTH_Y + HEALTH_H + 2.9f;               // frames leave a clear 1px line between them
    private static final float MANA_Y = STAMINA_Y + STAMINA_H + 2.9f, MANA_H = 6f;

    private static void drawBars(GuiGraphics g, Font font, Player p, float dt) {
        float hp = clamp01(p.getHealth() / Math.max(1f, p.getMaxHealth()));
        if (trailHealth < 0f || hp >= trailHealth) trailHealth = hp;
        else trailHealth = Math.max(hp, trailHealth - dt * 0.35f);

        int hpCol = p.hasEffect(MobEffects.WITHER) ? argb(64, 56, 60, 255)
                : p.hasEffect(MobEffects.POISON) ? argb(120, 156, 40, 255) : RED;
        int hpDark = p.hasEffect(MobEffects.WITHER) ? argb(30, 26, 28, 255) : p.hasEffect(MobEffects.POISON) ? argb(60, 84, 20, 255) : DRED;
        String hpText = Math.round(p.getHealth()) + "/" + Math.round(p.getMaxHealth());
        bar(g, font, CY + HEALTH_Y, HEALTH_H, HEALTH_LEN, hp, hpCol, hpDark, trailHealth, hpText);

        float[] st = ResourceSources.stamina();
        if (st != null) {
            bar(g, font, CY + STAMINA_Y, STAMINA_H, STAMINA_LEN, clamp01(st[0] / st[1]), CREAM, argb(120, 112, 96, 255), -1f,
                    Math.round(st[0]) + "/" + Math.round(st[1]));
        }
        if (HudConfig.SHOW_MANA_BAR.get()) {
            float[] mn = ResourceSources.mana();
            if (mn != null) {
                bar(g, font, CY + MANA_Y, MANA_H, STAMINA_LEN, clamp01(mn[0] / mn[1]), argb(92, 154, 255, 255), argb(36, 74, 176, 255), -1f,
                        Math.round(mn[0]) + "/" + Math.round(mn[1]));
            }
        }
        if (p.getAbsorptionAmount() > 0.05f) absorption(g, font, p.getAbsorptionAmount(), st != null);
    }

    /**
     * Absorption: a row of small gold slanted pips right beside the stamina bar, starting where it ends. At most eight
     * pips (more absorption just makes each pip worth more); the last pip fills partially. The number follows the row.
     * The pips use the stamina bar's slope and its inner fill height, so their top and bottom line up with it.
     */
    private static void absorption(GuiGraphics g, Font font, float ab, boolean hasStamina) {
        Matrix4f m = g.pose().last().pose();
        float y0 = CY + STAMINA_Y, ins = 1.1f;
        float ya = y0 + ins, yb = y0 + STAMINA_H - ins;
        float unit = 2f * (float) Math.ceil(ab / 16f);
        int pips = Math.max(1, Math.min(8, (int) Math.ceil(ab / unit)));
        float pw = 5.2f, gap = 1.3f, step = pw + gap;
        float base = hasStamina ? left(false, y0) + STAMINA_LEN + 2.2f + 3.6f : left(false, y0) + 2f;
        int top = argb(255, 226, 132, 255), bot = argb(196, 142, 54, 255);
        float lastX = base;
        for (int i = 0; i < pips; i++) {
            float f = i == pips - 1 ? clamp01((ab - i * unit) / unit) : 1f;
            if (f <= 0.01f) continue;
            float sl = base + i * step, w = pw * f;
            float[] q = stripPts(sl, y0, sl + w, ya, yb, 0f);
            float[] o = stripPts(sl - 0.7f, y0, sl + w + 0.7f, ya - 0.7f, yb + 0.7f, 0f);
            poly(m, withAlpha(INK, 215), o);
            polyColors(m, 0f, q, new int[] {top, top, bot, bot});
            line(m, withAlpha(WHITE, 120), q[0] + 0.2f, ya + 0.35f, q[2], ya + 0.35f, 0.5f);
            lastX = sl + w - (yb - y0) * 0.5f;
        }
        String t = String.valueOf(Math.round(ab));
        float ts = 0.66f;
        g.pose().pushPose();
        g.pose().translate(lastX + 5.2f, y0 + (STAMINA_H - 7f * ts) / 2f, 0f);
        g.pose().scale(ts, ts, 1f);
        g.drawString(font, t, 0, 0, argb(255, 226, 140, 255), true);
        g.pose().popPose();
    }

    /** Left boundary of a bar at height y: follows the crest's lower-right edge plus a gap. */
    private static float left(boolean upper, float y) {
        return edge(upper, y) + GAP;
    }

    /**
     * Slanted strip shared by every bar (health, stamina, absorption, mount): every edge leans down-left at 45 degrees.
     * 'start' is the left x at the bar's top edge y0 and 'xr' the right x there; ya..yb is the vertical range drawn.
     */
    private static float[] stripPts(float start, float y0, float xr, float ya, float yb, float insX) {
        return new float[] {
            start - (ya - y0) + insX, ya, xr - (ya - y0) - insX, ya,
            xr - (yb - y0) - insX, yb, start - (yb - y0) + insX, yb};
    }

    private static void strip(Matrix4f m, int color, float start, float y0, float xr, float ya, float yb, float insX) {
        poly(m, color, stripPts(start, y0, xr, ya, yb, insX));
    }

    /** Gold frame + dark backing of a bar; returns nothing, the caller fills the inside. */
    private static void frame(Matrix4f m, float start, float y0, float h, float xr) {
        float y1 = y0 + h;
        strip(m, withAlpha(GOLD, 210), start, y0, xr + 1.0f, y0 - 0.9f, y1 + 0.9f, -1.2f);
        strip(m, withAlpha(INK, 248), start, y0, xr, y0, y1, 0f);
    }

    private static void bar(GuiGraphics g, Font font, float y0, float h, float length, float pct, int col, int colDark, float trail, String text) {
        barAt(g, font, left(false, y0), y0, h, length, pct, col, colDark, trail, text);
    }

    /** One bar starting at x = start (at its top edge). Text is drawn inside as current/max. */
    private static void barAt(GuiGraphics g, Font font, float start, float y0, float h, float length, float pct, int col, int colDark, float trail, String text) {
        Matrix4f m = g.pose().last().pose();
        float y1 = y0 + h;
        float xr = start + length;
        frame(m, start, y0, h, xr);
        float ins = 1.1f, insX = ins * 1.45f;
        if (trail > pct) {
            float xt = start + length * clamp01(trail);
            strip(m, withAlpha(BONE, 235), start, y0, xt, y0 + ins, y1 - ins, insX);
        }
        if (pct > 0.002f) {
            float xf = start + length * clamp01(pct);
            strip(m, col, start, y0, xf, y0 + ins, y1 - ins, insX);
            float ym = y0 + h * 0.5f;
            strip(m, withAlpha(colDark, 205), start, y0, xf, ym, y1 - ins, insX);
        }
        line(m, withAlpha(GOLD, 230), start, y0, xr, y0, 0.7f);
        // value text inside the bar
        float ts = Math.min(0.72f, (h - 1.5f) / 8f);
        float cxText = (start + xr) / 2f - h / 2f;
        g.pose().pushPose();
        g.pose().translate(cxText, y0 + (h - 7f * ts) / 2f, 0f);
        g.pose().scale(ts, ts, 1f);
        g.drawString(font, text, Math.round(-font.width(text) / 2f), 0, WHITE, true);
        g.pose().popPose();
    }

    private static float edge(boolean upper, float y) {
        return upper ? CX + (y - (CY - R)) : CX + R - (y - CY);
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}

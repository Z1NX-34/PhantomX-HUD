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

/** Bottom-left: diamond crest (live model in front of the frame, clipped to it), angled bars, armor/food chips, XP bar. */
final class PlayerBlock {
    private PlayerBlock() {}

    static final float R = 28f;                       // crest half-diagonal
    private static final float HEALTH_LEN = 138f, STAMINA_LEN = 112f, MANA_LEN = 88f;

    /** Crest centre relative to the block origin (bottom-left anchor). */
    static final float CX = R + 5f, CY = -(R + 16f);

    private static float blockTx, blockTy, blockScale = 1f;
    private static float trailHealth = -1f;
    private static long lastNanos;

    static void render(GuiGraphics g, Player p, int sw, int sh, float scale) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;

        blockTx = HudConfig.MARGIN_X.get();
        blockTy = sh - HudConfig.MARGIN_Y.get();
        blockScale = scale;

        g.pose().pushPose();
        g.pose().translate(blockTx, blockTy, 0);
        g.pose().scale(scale, scale, 1f);
        drawCrest(g, p);
        drawBars(g, font, p, dt);
        if (HudConfig.SHOW_CHIPS.get()) drawChips(g, font, p);
        g.pose().popPose();

        if (HudConfig.SHOW_XP.get()) drawXp(g, font, p, sw, sh, scale);
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
        float[][] studs = {{CX + R + 3f, CY}, {CX, CY + R + 3f}, {CX - R - 3f, CY}};
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
            // lower-left and lower-right regions outside the diamond (both convex)
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
            float scale = ar * 1.45f;                             // pixels per block
            float feetY = ay - ar * 1.0f + scale * 1.8f;          // head top reaches the top vertex height
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

    private static final float GAP = 6f;

    private static void drawBars(GuiGraphics g, Font font, Player p, float dt) {
        float hp = clamp01(p.getHealth() / Math.max(1f, p.getMaxHealth()));
        if (trailHealth < 0f || hp >= trailHealth) trailHealth = hp;
        else trailHealth = Math.max(hp, trailHealth - dt * 0.35f);

        int hpCol = p.hasEffect(MobEffects.WITHER) ? argb(64, 56, 60, 255)
                : p.hasEffect(MobEffects.POISON) ? argb(120, 156, 40, 255) : RED;
        int hpDark = p.hasEffect(MobEffects.WITHER) ? argb(30, 26, 28, 255) : p.hasEffect(MobEffects.POISON) ? argb(60, 84, 20, 255) : DRED;
        String hpText = Math.round(p.getHealth()) + "/" + Math.round(p.getMaxHealth());
        float healthY = CY - R * 0.62f + 0.5f;
        bar(g, font, true, healthY, 11f, HEALTH_LEN, hp, hpCol, hpDark, trailHealth, hpText);
        if (p.getAbsorptionAmount() > 0f) {
            float ab = clamp01(p.getAbsorptionAmount() / Math.max(1f, p.getMaxHealth()));
            bar(g, font, true, healthY - 6.6f, 5.6f, HEALTH_LEN * 0.62f, ab, GOLD, argb(150, 110, 44, 255), -1f,
                    String.valueOf(Math.round(p.getAbsorptionAmount())));
        }

        float staminaY = CY + R * 0.20f;
        float[] st = ResourceSources.stamina();
        if (st != null) bar(g, font, false, staminaY, 8f, STAMINA_LEN, clamp01(st[0] / st[1]), CREAM, argb(120, 112, 96, 255), -1f,
                Math.round(st[0]) + "/" + Math.round(st[1]));
        float manaY = staminaY + 9f;
        float[] mn = ResourceSources.mana();
        if (mn != null) bar(g, font, false, manaY, 8f, MANA_LEN, clamp01(mn[0] / mn[1]), BLUE, argb(52, 70, 104, 255), -1f,
                Math.round(mn[0]) + "/" + Math.round(mn[1]));
    }

    /** Left boundary of a bar at height y: follows the crest edge plus a gap. */
    private static float left(boolean upper, float y) {
        return edge(upper, y) + GAP;
    }

    /** Right boundary runs parallel to the left one (same 45 degree slope). xr is the bar's outermost x (bottom for upper bars, top for lower). */
    private static float right(boolean upper, float y0, float h, float xr, float y) {
        return upper ? xr - h + (y - y0) : xr - (y - y0);
    }

    private static void strip(Matrix4f m, int color, boolean upper, float y0, float h, float ya, float yb, float xr, float insX) {
        poly(m, color,
                left(upper, ya) + insX, ya, right(upper, y0, h, xr, ya) - insX, ya,
                right(upper, y0, h, xr, yb) - insX, yb, left(upper, yb) + insX, yb);
    }

    /**
     * One bar whose left end follows the crest edge (upper=true follows the top-right slope, false the bottom-right one)
     * and whose right end follows the same slope. Text is drawn inside as current/max.
     */
    private static void bar(GuiGraphics g, Font font, boolean upper, float y0, float h, float length, float pct, int col, int colDark, float trail, String text) {
        Matrix4f m = g.pose().last().pose();
        float y1 = y0 + h;
        float start = Math.max(left(upper, y0), left(upper, y1));
        float xr = start + length;
        // frame, backing
        strip(m, withAlpha(GOLD, 210), upper, y0, h, y0 - 0.9f, y1 + 0.9f, xr + 1.0f, -1.2f);
        strip(m, withAlpha(INK, 248), upper, y0, h, y0, y1, xr, 0f);
        float ins = 1.1f, insX = ins * 1.45f;
        if (trail > pct) {
            float xt = start + length * clamp01(trail);
            strip(m, withAlpha(BONE, 235), upper, y0, h, y0 + ins, y1 - ins, xt, insX);
        }
        if (pct > 0.002f) {
            float xf = start + length * clamp01(pct);
            strip(m, col, upper, y0, h, y0 + ins, y1 - ins, xf, insX);
            float ym = y0 + h * 0.5f;
            strip(m, withAlpha(colDark, 205), upper, y0, h, ym, y1 - ins, xf, insX);
        }
        line(m, withAlpha(GOLD, 230), left(upper, y0), y0, right(upper, y0, h, xr, y0), y0, 0.7f);
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

    // ------------------------------------------------------------------------------------------ armor / food

    private static void drawChips(GuiGraphics g, Font font, Player p) {
        float y0 = CY + R * 0.20f + 19.5f;
        float x0 = CX + R - (y0 - CY) + GAP + 3f;
        int armor = p.getArmorValue(), food = p.getFoodData().getFoodLevel();
        float w = 40f;
        mini(g, font, x0, y0, w, true, clamp01(armor / 20f), argb(150, 168, 206, 255), argb(84, 100, 130, 255), armor);
        mini(g, font, x0 + w + 6f, y0, w, false, clamp01(food / 20f), argb(222, 150, 56, 255), argb(130, 84, 34, 255), food);
    }

    /** Small rectangular gauge: icon plate on the left end, fill with the value inside. */
    private static void mini(GuiGraphics g, Font font, float x, float y, float w, boolean armor, float pct, int col, int dark, int value) {
        Matrix4f m = g.pose().last().pose();
        float h = 8f, tab = 11f;
        rect(m, withAlpha(GOLD, 215), x - 0.8f, y - 0.8f, x + w + 0.8f, y + h + 0.8f);
        rect(m, withAlpha(INK, 248), x, y, x + w, y + h);
        // icon plate
        rect(m, withAlpha(argb(40, 36, 70, 255), 255), x, y, x + tab, y + h);
        line(m, withAlpha(GOLD, 190), x + tab, y, x + tab, y + h, 0.7f);
        g.pose().pushPose();
        g.pose().translate(x + tab / 2f, y + h / 2f, 0f);
        g.pose().scale(0.82f, 0.82f, 1f);
        Matrix4f mi = g.pose().last().pose();
        if (armor) shield(mi, 0f, 0f, col); else drumstick(mi, 0f, 0f, col);
        g.pose().popPose();
        // fill
        float fx = x + tab + 0.9f, fw = w - tab - 1.8f;
        if (pct > 0.002f) {
            rect(m, withAlpha(dark, 255), fx, y + 0.9f, fx + fw * pct, y + h - 0.9f);
            rect(m, col, fx, y + 0.9f, fx + fw * pct, y + h * 0.5f);
        }
        String t = String.valueOf(value);
        float ts = 0.72f;
        g.pose().pushPose();
        g.pose().translate(fx + fw / 2f, y + (h - 7f * ts) / 2f, 0f);
        g.pose().scale(ts, ts, 1f);
        g.drawString(font, t, Math.round(-font.width(t) / 2f), 0, WHITE, true);
        g.pose().popPose();
    }

    private static void shield(Matrix4f m, float x, float y, int tint) {
        poly(m, GOLD, x - 3.6f, y - 3.9f, x + 3.6f, y - 3.9f, x + 3.6f, y + 0.4f, x, y + 4.4f, x - 3.6f, y + 0.4f);
        poly(m, tint, x - 2.6f, y - 2.9f, x + 2.6f, y - 2.9f, x + 2.6f, y + 0.1f, x, y + 3.2f, x - 2.6f, y + 0.1f);
        poly(m, withAlpha(WHITE, 100), x - 2.6f, y - 2.9f, x, y - 2.9f, x, y + 3.2f, x - 2.6f, y + 0.1f);
    }

    private static void drumstick(Matrix4f m, float x, float y, int tint) {
        line(m, BONE, x + 0.5f, y + 0.5f, x + 3.4f, y + 3.4f, 1.3f);
        ellipse(m, BONE, x + 3.0f, y + 4.0f, 1.0f, 1.0f);
        ellipse(m, BONE, x + 4.0f, y + 3.0f, 1.0f, 1.0f);
        ellipse(m, GOLD, x - 1.2f, y - 1.2f, 3.5f, 3.2f);
        ellipse(m, tint, x - 1.2f, y - 1.2f, 2.8f, 2.5f);
        ellipse(m, withAlpha(WHITE, 110), x - 2.2f, y - 2.2f, 1.1f, 0.9f);
    }

    // ------------------------------------------------------------------------------------------ xp

    private static final int XP_GREEN = argb(96, 188, 104, 255), XP_DARK = argb(44, 112, 62, 255), XP_LIGHT = argb(190, 244, 170, 255);

    private static void drawXp(GuiGraphics g, Font font, Player p, int sw, int sh, float scale) {
        g.pose().pushPose();
        g.pose().translate(sw / 2f, sh - 22f - 13f, 0);
        g.pose().scale(scale, scale, 1f);
        Matrix4f m = g.pose().last().pose();
        float w = 110f, h = 4.4f, sl = 2.5f, x0 = -w / 2f;
        poly(m, withAlpha(GOLD, 180), x0 - 0.8f, -0.8f, x0 + w + 0.8f, -0.8f, x0 + w - sl + 0.3f, h + 0.8f, x0 - 0.8f, h + 0.8f);
        poly(m, withAlpha(INK, 245), x0 + sl, 0, x0 + w, 0, x0 + w - sl, h, x0, h);
        float fill = (w - 1.6f) * clamp01(p.experienceProgress);
        if (fill > 0.5f) {
            float fx0 = x0 + 0.8f, fx1 = fx0 + fill;
            // base: bright upper half, darker lower half
            poly(m, XP_DARK, fx0 + sl, 0.8f, fx1, 0.8f, fx1 - sl, h - 0.8f, fx0, h - 0.8f);
            poly(m, XP_GREEN, fx0 + sl, 0.8f, fx1, 0.8f, fx1 - sl * 0.5f, h * 0.5f, fx0 + sl * 0.5f, h * 0.5f);
            // diagonal stripe pattern, only where a full stripe fits
            for (float sx = fx0 + 4f; sx < fx1 - 3.2f; sx += 4.2f) {
                poly(m, withAlpha(XP_LIGHT, 120), sx + 1.6f, 0.8f, sx + 2.8f, 0.8f, sx + 1.2f, h - 0.8f, sx + 0f, h - 0.8f);
            }
            line(m, withAlpha(XP_LIGHT, 200), fx0 + sl, 0.9f, fx1, 0.9f, 0.5f);
        }
        String lvl = String.valueOf(p.experienceLevel);
        float tw = font.width(lvl);
        float hw = Math.max(9f, tw / 2f + 6f), hh = Math.max(8f, hw * 0.9f);
        diamondFill(m, INK, 0, h / 2f, hw, hh);
        diamondFill(m, withAlpha(argb(40, 90, 56, 255), 160), 0, h / 2f, hw - 2.4f, hh - 2.2f);
        diamondLine(m, GOLD, 0, h / 2f, hw, hh, 0.9f);
        diamondLine(m, withAlpha(XP_GREEN, 170), 0, h / 2f, hw - 3f, hh - 2.8f, 0.45f);
        g.drawString(font, lvl, Math.round(-tw / 2f), Math.round(h / 2f - 3.5f), BONE, true);
        g.pose().popPose();
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}

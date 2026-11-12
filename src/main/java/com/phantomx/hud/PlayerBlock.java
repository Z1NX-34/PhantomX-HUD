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
    private static final float HEALTH_LEN = 130f, STAMINA_LEN = 105f, MANA_LEN = 80f;

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

    private static void drawCrest(GuiGraphics g, Player p) {
        Matrix4f m = g.pose().last().pose();
        // frame first, so the model sits in front of it
        diamondLine(m, withAlpha(GOLD, 120), CX, CY, R + 3f, R + 3f, 0.6f);
        diamondLine(m, GOLD, CX, CY, R, R, 1.5f);
        diamondLine(m, withAlpha(PURP, 210), CX, CY, R - 4f, R - 4f, 0.8f);
        diamondLine(m, withAlpha(GOLD, 90), CX, CY, R - 6.5f, R - 6.5f, 0.5f);

        drawModel(g, p);

        m = g.pose().last().pose();
        float[][] studs = {{CX, CY - R - 3f}, {CX + R + 3f, CY}, {CX, CY + R + 3f}, {CX - R - 3f, CY}};
        for (float[] s : studs) {
            diamondFill(m, INK, s[0], s[1], 2.8f, 2.8f);
            diamondFill(m, GOLD, s[0], s[1], 2f, 2f);
        }
    }

    /**
     * The model is drawn after the frame, with a depth mask over everything outside the diamond so nothing of it can
     * leave the frame. No stencil buffer is needed.
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
            // four corner triangles between the diamond and its bounding box
            polyZ(m, WHITE, Z, CX - r, CY - r, CX, CY - r, CX - r, CY);
            polyZ(m, WHITE, Z, CX + r, CY - r, CX, CY - r, CX + r, CY);
            polyZ(m, WHITE, Z, CX - r, CY + r, CX, CY + r, CX - r, CY);
            polyZ(m, WHITE, Z, CX + r, CY + r, CX, CY + r, CX + r, CY);
            // four bands around the bounding box
            polyZ(m, WHITE, Z, CX - B, CY - B, CX + B, CY - B, CX + B, CY - r, CX - B, CY - r);
            polyZ(m, WHITE, Z, CX - B, CY + r, CX + B, CY + r, CX + B, CY + B, CX - B, CY + B);
            polyZ(m, WHITE, Z, CX - B, CY - r, CX - r, CY - r, CX - r, CY + r, CX - B, CY + r);
            polyZ(m, WHITE, Z, CX + r, CY - r, CX + B, CY - r, CX + B, CY + r, CX + r, CY + r);
        } finally {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        }

        g.pose().pushPose();
        g.pose().setIdentity();
        try {
            float ax = blockTx + blockScale * CX, ay = blockTy + blockScale * CY, ar = blockScale * R;
            float scale = ar * 1.3f;                              // pixels per block
            float feetY = ay - ar * 0.80f + scale * 1.8f;         // head top sits near the upper part of the diamond
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

    private static void drawBars(GuiGraphics g, Font font, Player p, float dt) {
        float hp = clamp01(p.getHealth() / Math.max(1f, p.getMaxHealth()));
        if (trailHealth < 0f || hp >= trailHealth) trailHealth = hp;
        else trailHealth = Math.max(hp, trailHealth - dt * 0.35f);

        int hpCol = p.hasEffect(MobEffects.WITHER) ? argb(64, 56, 60, 255)
                : p.hasEffect(MobEffects.POISON) ? argb(120, 156, 40, 255) : RED;
        int hpDark = p.hasEffect(MobEffects.WITHER) ? argb(30, 26, 28, 255) : p.hasEffect(MobEffects.POISON) ? argb(60, 84, 20, 255) : DRED;
        float healthY = CY - R * 0.62f + 2.0f;
        float xe = angledBar(g, true, healthY, 8f, HEALTH_LEN, hp, hpCol, hpDark, trailHealth);
        if (p.getAbsorptionAmount() > 0f) {
            float ab = clamp01(p.getAbsorptionAmount() / Math.max(1f, p.getMaxHealth()));
            float x0 = edge(true, healthY + 9.8f) + 6f;
            line(g.pose().last().pose(), argb(255, 214, 110, 255), x0, healthY + 9.8f, x0 + HEALTH_LEN * ab, healthY + 9.8f, 1.3f);
        }
        g.drawString(font, String.valueOf(Math.round(p.getHealth())), Math.round(xe + 5f), Math.round(healthY), WHITE, true);

        float st = ResourceSources.stamina();
        if (st >= 0f) angledBar(g, false, CY + R * 0.20f, 5.5f, STAMINA_LEN, st, CREAM, argb(120, 112, 96, 255), -1f);
        float mn = ResourceSources.mana();
        if (mn >= 0f) angledBar(g, false, CY + R * 0.20f + 8f, 4.5f, MANA_LEN, mn, BLUE, argb(52, 70, 104, 255), -1f);
    }

    /**
     * One bar whose left end follows the crest edge. upper=true follows the '/' edge (top vertex to right vertex), false
     * follows the '\' edge (right vertex to bottom vertex). Returns the x of the bar's right end.
     */
    private static float angledBar(GuiGraphics g, boolean upper, float y0, float h, float length, float pct, int col, int colDark, float trail) {
        Matrix4f m = g.pose().last().pose();
        float y1 = y0 + h, gap = 6f, sl = 4.5f;
        float xTop = edge(upper, y0) + gap, xBot = edge(upper, y1) + gap;
        float x0 = Math.max(xTop, xBot), xe = x0 + length;
        // frame
        poly(m, withAlpha(GOLD, 200), xTop - 0.8f, y0 - 0.8f, xe + 0.8f, y0 - 0.8f, xe - sl + 0.4f, y1 + 0.8f, xBot - 0.8f, y1 + 0.8f);
        poly(m, withAlpha(INK, 245), xTop, y0, xe, y0, xe - sl, y1, xBot, y1);
        float ins = 1f;
        if (trail > pct) {
            float xt = x0 + length * trail;
            poly(m, withAlpha(BONE, 235), xTop + ins, y0 + ins, xt - ins, y0 + ins, xt - ins - sl, y1 - ins, xBot + ins, y1 - ins);
        }
        float xf = x0 + length * clamp01(pct);
        if (pct > 0.001f) {
            float ym = y0 + (y1 - y0) * 0.5f;
            poly(m, col, xTop + ins, y0 + ins, xf - ins, y0 + ins, xf - ins - sl, y1 - ins, xBot + ins, y1 - ins);
            // darker lower half for depth
            poly(m, withAlpha(colDark, 200), edge(upper, ym) + gap + ins, ym, xf - ins - sl * 0.5f, ym, xf - ins - sl, y1 - ins, xBot + ins, y1 - ins);
        }
        line(m, withAlpha(GOLD, 220), xTop, y0, xe, y0, 0.7f);
        return xe;
    }

    private static float edge(boolean upper, float y) {
        return upper ? CX + (y - (CY - R)) : CX + R - (y - CY);
    }

    // ------------------------------------------------------------------------------------------ chips

    private static void drawChips(GuiGraphics g, Font font, Player p) {
        float y = CY + R + 11f;
        chip(g, font, CX - 22f, y, true, String.valueOf(p.getArmorValue()), p.getArmorValue() > 0);
        chip(g, font, CX + 8f, y, false, String.valueOf(p.getFoodData().getFoodLevel()), p.getFoodData().getFoodLevel() > 6);
    }

    private static void chip(GuiGraphics g, Font font, float x, float y, boolean armor, String text, boolean lit) {
        Matrix4f m = g.pose().last().pose();
        diamondFill(m, withAlpha(INK, 235), x, y, 7f, 7f);
        diamondLine(m, lit ? GOLD : DGOLD, x, y, 7f, 7f, 0.8f);
        if (armor) shield(m, x, y, lit); else drumstick(m, x, y, lit);
        g.drawString(font, text, Math.round(x + 10f), Math.round(y - 4f), lit ? BONE : argb(150, 144, 130, 255), true);
    }

    private static void shield(Matrix4f m, float x, float y, boolean lit) {
        int steel = lit ? argb(170, 182, 214, 255) : argb(96, 100, 118, 255);
        poly(m, GOLD, x - 3.6f, y - 3.9f, x + 3.6f, y - 3.9f, x + 3.6f, y + 0.4f, x, y + 4.4f, x - 3.6f, y + 0.4f);
        poly(m, steel, x - 2.7f, y - 3.0f, x + 2.7f, y - 3.0f, x + 2.7f, y + 0.1f, x, y + 3.2f, x - 2.7f, y + 0.1f);
        poly(m, withAlpha(WHITE, 90), x - 2.7f, y - 3.0f, x, y - 3.0f, x, y + 3.2f, x - 2.7f, y + 0.1f);
    }

    private static void drumstick(Matrix4f m, float x, float y, boolean lit) {
        int meat = lit ? argb(214, 128, 62, 255) : argb(120, 84, 56, 255);
        line(m, BONE, x + 0.5f, y + 0.5f, x + 3.4f, y + 3.4f, 1.3f);
        ellipse(m, BONE, x + 3.0f, y + 4.1f, 1.0f, 1.0f);
        ellipse(m, BONE, x + 4.1f, y + 3.0f, 1.0f, 1.0f);
        ellipse(m, GOLD, x - 1.2f, y - 1.2f, 3.5f, 3.2f);
        ellipse(m, meat, x - 1.2f, y - 1.2f, 2.8f, 2.5f);
        ellipse(m, withAlpha(WHITE, 90), x - 2.2f, y - 2.2f, 1.1f, 0.9f);
    }

    // ------------------------------------------------------------------------------------------ xp

    private static void drawXp(GuiGraphics g, Font font, Player p, int sw, int sh, float scale) {
        g.pose().pushPose();
        g.pose().translate(sw / 2f, sh - 22f - 12f, 0);
        g.pose().scale(scale, scale, 1f);
        Matrix4f m = g.pose().last().pose();
        float w = 105f, h = 3.2f, sl = 2.5f, x0 = -w / 2f;
        poly(m, withAlpha(GOLD, 170), x0 - 0.7f, -0.7f, x0 + w + 0.7f, -0.7f, x0 + w - sl + 0.2f, h + 0.7f, x0 - 0.7f, h + 0.7f);
        poly(m, withAlpha(INK, 240), x0 + sl, 0, x0 + w, 0, x0 + w - sl, h, x0, h);
        float fill = (w - 1f) * clamp01(p.experienceProgress);
        if (fill > 0.5f) poly(m, withAlpha(GOLD, 245), x0 + sl + 0.5f, 0.5f, x0 + 0.5f + fill, 0.5f, x0 + fill - sl + 0.5f, h - 0.5f, x0 + 0.5f, h - 0.5f);
        diamondFill(m, INK, 0, h / 2f, 7f, 6f);
        diamondLine(m, GOLD, 0, h / 2f, 7f, 6f, 0.8f);
        String lvl = String.valueOf(p.experienceLevel);
        g.drawString(font, lvl, Math.round(-font.width(lvl) / 2f), Math.round(h / 2f - 4f), BONE, true);
        g.pose().popPose();
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}

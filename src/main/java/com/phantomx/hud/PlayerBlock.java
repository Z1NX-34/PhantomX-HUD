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
        drawCrest(g, font, p, dt);
        drawSecondaryBars(g, font);
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
    private static void drawCrest(GuiGraphics g, Font font, Player p, float dt) {
        Matrix4f m = g.pose().last().pose();
        // upper half: behind the model
        diamondHalf(m, withAlpha(GOLD, 120), CX, CY, R + 3f, R + 3f, 0.6f, true);
        diamondHalf(m, GOLD, CX, CY, R, R, 1.5f, true);
        diamondHalf(m, withAlpha(PURP, 210), CX, CY, R - 4f, R - 4f, 0.8f, true);
        diamondHalf(m, withAlpha(GOLD, 90), CX, CY, R - 6.5f, R - 6.5f, 0.5f, true);
        diamondFill(m, INK, CX, CY - R - 3f, 2.8f, 2.8f);
        diamondFill(m, GOLD, CX, CY - R - 3f, 2f, 2f);

        // the health bar (and absorption) belong to the upper slope: behind the model like the upper frame
        drawHealth(g, font, p, dt);

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

    private static void drawHealth(GuiGraphics g, Font font, Player p, float dt) {
        float hp = clamp01(p.getHealth() / Math.max(1f, p.getMaxHealth()));
        if (trailHealth < 0f || hp >= trailHealth) trailHealth = hp;
        else trailHealth = Math.max(hp, trailHealth - dt * 0.35f);

        int hpCol = p.hasEffect(MobEffects.WITHER) ? argb(64, 56, 60, 255)
                : p.hasEffect(MobEffects.POISON) ? argb(120, 156, 40, 255) : RED;
        int hpDark = p.hasEffect(MobEffects.WITHER) ? argb(30, 26, 28, 255) : p.hasEffect(MobEffects.POISON) ? argb(60, 84, 20, 255) : DRED;
        String hpText = Math.round(p.getHealth()) + "/" + Math.round(p.getMaxHealth());
        float healthY = CY - R * 0.62f + 1.5f;
        bar(g, font, true, healthY, 9.5f, HEALTH_LEN, hp, hpCol, hpDark, trailHealth, hpText);
        if (p.getAbsorptionAmount() > 0.05f) absorption(g, font, p.getAbsorptionAmount(), healthY);
    }

    private static void drawSecondaryBars(GuiGraphics g, Font font) {
        float staminaY = CY + R * 0.20f - 1f;
        float[] st = ResourceSources.stamina();
        if (st != null) bar(g, font, false, staminaY, 8f, STAMINA_LEN, clamp01(st[0] / st[1]), CREAM, argb(120, 112, 96, 255), -1f,
                Math.round(st[0]) + "/" + Math.round(st[1]));
        float manaY = CY + R * 0.20f + 9f;
        float[] mn = ResourceSources.mana();
        if (mn != null) bar(g, font, false, manaY, 8f, MANA_LEN, clamp01(mn[0] / mn[1]), BLUE, argb(52, 70, 104, 255), -1f,
                Math.round(mn[0]) + "/" + Math.round(mn[1]));
    }

    /**
     * Absorption: no frame and no empty slots. A row of small gold slanted pips sits on the upper slope above the health
     * bar and simply grows with the absorption amount (2 HP per pip, more per pip when it gets large); the last pip
     * fills partially. The number sits at the end of the row.
     */
    private static void absorption(GuiGraphics g, Font font, float ab, float healthY) {
        Matrix4f m = g.pose().last().pose();
        float h = 3.8f, y0 = healthY - h - 2.6f, y1 = y0 + h;
        float unit = 2f * (float) Math.ceil(ab / 40f);
        int pips = Math.max(1, (int) Math.ceil(ab / unit));
        float pw = 5.2f, gap = 1.3f, step = pw + gap;
        float xa0 = left(true, y0);
        int top = argb(255, 226, 132, 255), bot = argb(196, 142, 54, 255);
        float lastX = xa0;
        for (int i = 0; i < pips; i++) {
            float f = i == pips - 1 ? clamp01((ab - i * unit) / unit) : 1f;
            if (f <= 0.01f) continue;
            float xa = xa0 + i * step, w = pw * f;
            poly(m, withAlpha(INK, 215),
                    xa - 0.7f, y0 - 0.7f, xa + w + 0.7f, y0 - 0.7f, xa + w + h + 0.7f, y1 + 0.7f, xa + h - 0.7f, y1 + 0.7f);
            polyColors(m, 0f, new float[] {xa, y0, xa + w, y0, xa + w + h, y1, xa + h, y1}, new int[] {top, top, bot, bot});
            line(m, withAlpha(WHITE, 120), xa + 0.2f, y0 + 0.35f, xa + w, y0 + 0.35f, 0.5f);
            lastX = xa + w + h * 0.5f;
        }
        String t = String.valueOf(Math.round(ab));
        float ts = 0.66f;
        g.pose().pushPose();
        g.pose().translate(lastX + 3.2f, y0 + (h - 7f * ts) / 2f, 0f);
        g.pose().scale(ts, ts, 1f);
        g.drawString(font, t, 0, 0, argb(255, 226, 140, 255), true);
        g.pose().popPose();
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
        float h = 8.5f, y0 = CY + R * 0.20f + 19.5f;
        float start = left(false, y0);                 // top-left corner of the first chip, on the crest's lower slope
        float w = 46f, gap = 3.2f;
        int armor = p.getArmorValue(), food = p.getFoodData().getFoodLevel();
        chip(g, font, start, y0, h, w, true, clamp01(armor / 20f), armor);
        chip(g, font, start + w + gap, y0, h, w, false, clamp01(food / 20f), food);
    }

    /** Parallelogram whose top edge starts at xt0..xt1 (at y0) and leans down-left at 45 degrees, like the lower bars. */
    private static float[] lean(float y0, float xt0, float xt1, float ya, float yb) {
        float sa = ya - y0, sb = yb - y0;
        return new float[] {xt0 - sa, ya, xt1 - sa, ya, xt1 - sb, yb, xt0 - sb, yb};
    }

    /** Armor / food gauge: slanted at both ends like the bars, icon plate on the left, value inside the fill. */
    private static void chip(GuiGraphics g, Font font, float xt, float y0, float h, float w, boolean armor, float pct, int value) {
        Matrix4f m = g.pose().last().pose();
        float y1 = y0 + h, tab = 13f;
        int col = armor ? argb(150, 192, 246, 255) : argb(242, 164, 62, 255);
        int dark = armor ? argb(58, 90, 150, 255) : argb(138, 74, 30, 255);
        poly(m, withAlpha(GOLD, 215), lean(y0, xt - 1.1f, xt + w + 1.1f, y0 - 0.9f, y1 + 0.9f));
        poly(m, withAlpha(INK, 248), lean(y0, xt, xt + w, y0, y1));
        polyColors(m, 0f, lean(y0, xt, xt + tab, y0, y1),
                new int[] {argb(50, 46, 88, 255), argb(50, 46, 88, 255), argb(24, 22, 46, 255), argb(24, 22, 46, 255)});
        line(m, withAlpha(GOLD, 190), xt + tab, y0, xt + tab - h, y1, 0.7f);

        g.pose().pushPose();
        g.pose().translate(xt + tab / 2f - h / 2f, y0 + h / 2f, 0f);
        g.pose().scale(0.82f, 0.82f, 1f);
        Matrix4f mi = g.pose().last().pose();
        if (armor) shield(mi, 0f, 0f); else drumstick(mi, 0f, 0f);
        g.pose().popPose();

        float fx0 = xt + tab + 0.9f, fw = w - tab - 1.8f;
        if (pct > 0.002f) {
            polyColors(m, 0f, lean(y0, fx0, fx0 + fw * pct, y0 + 0.9f, y1 - 0.9f), new int[] {col, col, dark, dark});
            poly(m, withAlpha(WHITE, 70), lean(y0, fx0, fx0 + fw * pct, y0 + 0.9f, y0 + h * 0.4f));
        }
        String t = String.valueOf(value);
        float ts = 0.72f;
        g.pose().pushPose();
        g.pose().translate((fx0 + fx0 + fw) / 2f - h / 2f, y0 + (h - 7f * ts) / 2f, 0f);
        g.pose().scale(ts, ts, 1f);
        g.drawString(font, t, Math.round(-font.width(t) / 2f), 0, WHITE, true);
        g.pose().popPose();
    }

    /** Shield: gold rim, steel face with a bevel, centre ridge and a gold boss. */
    private static void shield(Matrix4f m, float x, float y) {
        int g1 = argb(248, 218, 130, 255), g2 = argb(168, 120, 46, 255);
        polyColors(m, 0f, new float[] {x - 4.3f, y - 4.5f, x + 4.3f, y - 4.5f, x + 4.3f, y + 0.7f, x, y + 5.1f, x - 4.3f, y + 0.7f},
                new int[] {g1, g1, g2, g2, g2});
        int f1 = argb(170, 208, 255, 255), f2 = argb(60, 92, 150, 255);
        polyColors(m, 0f, new float[] {x - 3.3f, y - 3.6f, x + 3.3f, y - 3.6f, x + 3.3f, y + 0.4f, x, y + 4.0f, x - 3.3f, y + 0.4f},
                new int[] {f1, f1, f2, f2, f2});
        poly(m, withAlpha(WHITE, 70), x - 3.3f, y - 3.6f, x, y - 3.6f, x, y + 4.0f, x - 3.3f, y + 0.4f);
        poly(m, withAlpha(INK, 80), x, y - 3.6f, x + 3.3f, y - 3.6f, x + 3.3f, y + 0.4f, x, y + 4.0f);
        line(m, withAlpha(WHITE, 130), x - 3.0f, y - 3.4f, x + 3.0f, y - 3.4f, 0.5f);
        diamondFill(m, GOLD, x, y - 0.6f, 1.2f, 1.2f);
        diamondFill(m, withAlpha(WHITE, 150), x - 0.2f, y - 0.8f, 0.4f, 0.4f);
    }

    /** Drumstick: shaded meat with a highlight and a bone with two knobs. */
    private static void drumstick(Matrix4f m, float x, float y) {
        line(m, BONE, x + 0.6f, y + 0.6f, x + 3.9f, y + 3.9f, 1.5f);
        ellipse(m, BONE, x + 3.6f, y + 4.7f, 0.95f, 0.95f);
        ellipse(m, BONE, x + 4.7f, y + 3.6f, 0.95f, 0.95f);
        ellipse(m, argb(104, 48, 20, 255), x - 1.1f, y - 1.1f, 4.0f, 3.6f);
        ellipse(m, argb(160, 80, 32, 255), x - 1.1f, y - 1.1f, 3.4f, 3.0f);
        ellipse(m, argb(240, 150, 64, 255), x - 1.5f, y - 1.5f, 2.7f, 2.3f);
        ellipse(m, withAlpha(WHITE, 130), x - 2.4f, y - 2.4f, 1.0f, 0.7f);
    }

    // ------------------------------------------------------------------------------------------ xp

    private static final int XP_TOP = argb(120, 238, 152, 255), XP_BOT = argb(32, 130, 84, 255), XP_MINT = argb(190, 255, 214, 255);

    /**
     * Modern XP bar: ten slanted segments (five each side of the level diamond) that light up one after another with a
     * green gradient, a bright leading edge on the segment being filled and a soft sheen that travels along the lit part.
     */
    private static void drawXp(GuiGraphics g, Font font, Player p, int sw, int sh, float scale) {
        g.pose().pushPose();
        g.pose().translate(sw / 2f, sh - 22f - 16f, 0);
        g.pose().scale(scale, scale, 1f);
        Matrix4f m = g.pose().last().pose();
        final int segs = 10;
        float segW = 8.6f, gap = 1.5f, sl = 2.4f, h = 3.6f, step = segW + gap, len = segW - sl;

        float inner = 15f;

        float progress = clamp01(p.experienceProgress) * segs;
        float wave = (System.currentTimeMillis() % 2800L) / 2800f * (segs + 4) - 2f;
        for (int i = 0; i < segs; i++) {
            float xL = i < segs / 2 ? -inner - (segs / 2 - i) * step + gap : inner + (i - segs / 2) * step;
            // faint gold edge + dark backing
            poly(m, withAlpha(GOLD, 95), skewBand(xL - 0.6f, sl, h, -0.6f, h + 0.6f, 0f, len + 1.2f));
            poly(m, withAlpha(INK, 238), skewBand(xL, sl, h, 0f, h, 0f, len));
            float f = clamp01(progress - i);
            if (f <= 0.001f) continue;
            float u1 = len * f;
            polyColors(m, 0f, skewBand(xL, sl, h, 0f, h, 0f, u1), new int[] {XP_TOP, XP_TOP, XP_BOT, XP_BOT});
            float k = Math.max(0f, 1f - Math.abs(i - wave) / 1.6f);
            if (k > 0f) poly(m, withAlpha(WHITE, Math.round(70 * k)), skewBand(xL, sl, h, 0f, h, 0f, u1));
            poly(m, withAlpha(WHITE, 130), skewBand(xL, sl, h, 0f, h * 0.28f, 0f, u1));
            if (f < 0.999f) {
                float xt = xL + u1 + sl, xb = xL + u1;
                line(m, withAlpha(XP_MINT, 70), xt, -0.5f, xb, h + 0.5f, 3.2f);
                line(m, withAlpha(WHITE, 235), xt, -0.2f, xb, h + 0.2f, 1.0f);
            }
        }

        // level badge: fixed size, softly rounded diamond, up to three digits
        float cy = h / 2f, hw = 10.5f;
        poly(m, GOLD, roundedDiamond(0, cy, hw + 1.4f, hw + 1.4f, 4.6f));
        poly(m, INK, roundedDiamond(0, cy, hw, hw, 4.1f));
        poly(m, withAlpha(XP_TOP, 150), roundedDiamond(0, cy, hw - 1.0f, hw - 1.0f, 3.7f));
        poly(m, argb(20, 44, 36, 255), roundedDiamond(0, cy, hw - 1.9f, hw - 1.9f, 3.3f));
        diamondFill(m, GOLD, 0, cy - hw - 2.8f, 1.5f, 1.5f);
        diamondFill(m, GOLD, 0, cy + hw + 2.8f, 1.5f, 1.5f);
        String lvl = String.valueOf(Math.min(999, p.experienceLevel));
        float ts = lvl.length() >= 3 ? 0.8f : 1f, tw = font.width(lvl);
        g.pose().pushPose();
        g.pose().translate(0f, cy, 0f);
        g.pose().scale(ts, ts, 1f);
        g.drawString(font, lvl, Math.round(-tw / 2f), -4, BONE, true);
        g.pose().popPose();
        g.pose().popPose();
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}

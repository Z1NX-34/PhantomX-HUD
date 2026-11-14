package com.phantomx.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/** Small shape painter for GUI space. Colours are ARGB ints. Face culling is switched off so winding never matters. */
final class HudDraw {
    private HudDraw() {}

    // palette
    static final int INK = argb(14, 12, 14, 255);
    static final int BONE = argb(236, 228, 212, 255);
    static final int RED = argb(196, 32, 38, 255);
    static final int DRED = argb(120, 18, 24, 255);
    static final int GOLD = argb(214, 174, 84, 255);
    static final int DGOLD = argb(150, 110, 44, 255);
    static final int PURP = argb(96, 76, 150, 255);
    static final int CREAM = argb(222, 214, 194, 255);
    static final int BLUE = argb(120, 156, 204, 255);
    static final int WHITE = 0xFFFFFFFF;

    static int argb(int r, int g, int b, int a) {
        return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
    }

    static int withAlpha(int color, int a) {
        return (color & 0xFFFFFF) | ((Math.max(0, Math.min(255, a))) << 24);
    }

    static int scaleAlpha(int color, float f) {
        return withAlpha(color, Math.round((color >>> 24) * Math.max(0f, Math.min(1f, f))));
    }

    /** Filled convex polygon at z=0. xy = x0,y0,x1,y1,... */
    static void poly(Matrix4f m, int color, float... xy) {
        polyZ(m, color, 0f, xy);
    }

    static void polyZ(Matrix4f m, int color, float z, float... xy) {
        int n = xy.length / 2;
        int[] cols = new int[n];
        java.util.Arrays.fill(cols, color);
        polyColors(m, z, xy, cols);
    }

    /** Convex polygon with one colour per vertex. */
    static void polyColors(Matrix4f m, float z, float[] xy, int[] cols) {
        int n = xy.length / 2;
        if (n < 3) return;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        Tesselator t = Tesselator.getInstance();
        BufferBuilder bb = t.getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < n; i++) {
            int c = cols[i];
            bb.vertex(m, xy[i * 2], xy[i * 2 + 1], z)
                    .color(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24 & 255).endVertex();
        }
        t.end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** Rectangle with a horizontal colour gradient. */
    static void gradH(Matrix4f m, float x0, float y0, float x1, float y1, int left, int right) {
        polyColors(m, 0f, new float[] {x0, y0, x1, y0, x1, y1, x0, y1}, new int[] {left, right, right, left});
    }

    static void rect(Matrix4f m, int color, float x0, float y0, float x1, float y1) {
        poly(m, color, x0, y0, x1, y0, x1, y1, x0, y1);
    }

    static void line(Matrix4f m, int color, float x1, float y1, float x2, float y2, float w) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return;
        float nx = -dy / len * w / 2f, ny = dx / len * w / 2f;
        poly(m, color, x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, x1 - nx, y1 - ny);
    }

    static void ellipse(Matrix4f m, int color, float cx, float cy, float rx, float ry) {
        int seg = 18;
        float[] xy = new float[seg * 2];
        for (int i = 0; i < seg; i++) {
            double a = Math.PI * 2 * i / seg;
            xy[i * 2] = cx + (float) Math.cos(a) * rx;
            xy[i * 2 + 1] = cy + (float) Math.sin(a) * ry;
        }
        poly(m, color, xy);
    }

    static void diamondFill(Matrix4f m, int color, float cx, float cy, float hw, float hh) {
        poly(m, color, cx, cy - hh, cx + hw, cy, cx, cy + hh, cx - hw, cy);
    }

    static void diamondLine(Matrix4f m, int color, float cx, float cy, float hw, float hh, float w) {
        line(m, color, cx, cy - hh, cx + hw, cy, w);
        line(m, color, cx + hw, cy, cx, cy + hh, w);
        line(m, color, cx, cy + hh, cx - hw, cy, w);
        line(m, color, cx - hw, cy, cx, cy - hh, w);
    }

    /** Upper or lower half of a diamond outline (left vertex - top/bottom vertex - right vertex). */
    static void diamondHalf(Matrix4f m, int color, float cx, float cy, float hw, float hh, float w, boolean upper) {
        float vy = upper ? cy - hh : cy + hh;
        line(m, color, cx - hw, cy, cx, vy, w);
        line(m, color, cx, vy, cx + hw, cy, w);
    }

    /**
     * Skewed band of a slanted bar: x runs from x0+u0 to x0+u1 (u in 0..len), every point is shifted right by
     * sl*(1 - y/h), so the left and right ends lean the same way. Returns xy for poly().
     */
    static float[] skewBand(float x0, float sl, float h, float ya, float yb, float u0, float u1) {
        float sa = sl * (1f - ya / h), sb = sl * (1f - yb / h);
        return new float[] {x0 + u0 + sa, ya, x0 + u1 + sa, ya, x0 + u1 + sb, yb, x0 + u0 + sb, yb};
    }

    /**
     * Starts clipping to a diamond of half-diagonal hm around (cx, cy) in the current pose: until endDiamondClip,
     * everything drawn inside this call pair only shows within the diamond. Done with a depth mask (no stencil needed).
     */
    static void beginDiamondClip(GuiGraphics g, float cx, float cy, float hm, float ext) {
        Matrix4f m = g.pose().last().pose();
        g.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.colorMask(false, false, false, false);
        try {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    polyZ(m, WHITE, 300f,
                            cx + sx * hm, cy, cx + sx * ext, cy, cx + sx * ext, cy + sy * ext, cx, cy + sy * ext, cx, cy + sy * hm);
                }
            }
        } finally {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        }
    }

    static void endDiamondClip(GuiGraphics g) {
        g.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }

    /** Diamond with softly rounded vertices (quadratic corner arcs), as a convex fan polygon for poly(). */
    static float[] roundedDiamond(float cx, float cy, float hw, float hh, float r) {
        float[][] v = {{cx, cy - hh}, {cx + hw, cy}, {cx, cy + hh}, {cx - hw, cy}};
        int arc = 7;
        float[] out = new float[4 * (arc + 1) * 2];
        int n = 0;
        for (int i = 0; i < 4; i++) {
            float[] cur = v[i], prev = v[(i + 3) % 4], next = v[(i + 1) % 4];
            float ax = prev[0] - cur[0], ay = prev[1] - cur[1], al = (float) Math.sqrt(ax * ax + ay * ay);
            float bx = next[0] - cur[0], by = next[1] - cur[1], bl = (float) Math.sqrt(bx * bx + by * by);
            float sx = cur[0] + ax / al * r, sy = cur[1] + ay / al * r;
            float ex = cur[0] + bx / bl * r, ey = cur[1] + by / bl * r;
            for (int k = 0; k <= arc; k++) {
                float t = k / (float) arc, u = 1f - t;
                out[n++] = u * u * sx + 2f * u * t * cur[0] + t * t * ex;
                out[n++] = u * u * sy + 2f * u * t * cur[1] + t * t * ey;
            }
        }
        return out;
    }
}

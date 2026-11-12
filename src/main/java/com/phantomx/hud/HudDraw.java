package com.phantomx.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/** Tiny shape painter for GUI space: filled convex polygons, thin lines and diamonds. Colours are ARGB ints. */
final class HudDraw {
    private HudDraw() {}

    static int argb(int r, int g, int b, int a) {
        return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
    }

    static int withAlpha(int color, int a) {
        return (color & 0xFFFFFF) | ((a & 255) << 24);
    }

    /** Filled convex polygon. xy = x0,y0,x1,y1,... */
    static void poly(Matrix4f m, int color, float... xy) {
        if (xy.length < 6) return;
        float a = (color >>> 24 & 255) / 255f, r = (color >> 16 & 255) / 255f, g = (color >> 8 & 255) / 255f, b = (color & 255) / 255f;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Tesselator t = Tesselator.getInstance();
        BufferBuilder bb = t.getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i + 1 < xy.length; i += 2) bb.vertex(m, xy[i], xy[i + 1], 0f).color(r, g, b, a).endVertex();
        t.end();
        RenderSystem.disableBlend();
    }

    /** Line with thickness, as a rotated quad. */
    static void line(Matrix4f m, int color, float x1, float y1, float x2, float y2, float w) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return;
        float nx = -dy / len * w / 2f, ny = dx / len * w / 2f;
        poly(m, color, x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, x1 - nx, y1 - ny);
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
}

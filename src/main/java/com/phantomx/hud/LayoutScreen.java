package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.GOLD;
import static com.phantomx.hud.HudDraw.WHITE;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Layout editor. The live HUD stays visible underneath; every module gets a box. Drag a box to move it, drag a corner or
 * use the scroll wheel to resize it, arrows nudge, R resets the selected module. Edges snap to the screen edges / centre
 * and to other modules (hold Alt to switch snapping off). Everything is saved when the screen closes.
 */
final class LayoutScreen extends Screen {
    private static final int SNAP = 4, HANDLE = 5;
    private static final int BOX = 0xFFB59CFF, BOX_HOVER = 0xFFFFFFFF, VEIL = 0x38000000;

    private enum Drag { NONE, MOVE, SIZE }

    private Layout.Mod sel;
    private Drag drag = Drag.NONE;
    private double smx, smy;
    private float sdx, sdy;
    private float[] startRect;
    private float fx, fy, w0, h0, s0;
    private int hx, hy;
    private float guideX = Float.NaN, guideY = Float.NaN;

    LayoutScreen() {
        super(Component.literal("PhantomX HUD layout"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Reset all"), b -> {
            Layout.resetAll();
            sel = null;
        }).bounds(width / 2 - 84, height / 2 + 8, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 + 4, height / 2 + 8, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Preset: " + Layout.itemsStyle().label), b -> {
            Layout.setItemsStyle(Layout.itemsStyle().next());
            b.setMessage(Component.literal("Preset: " + Layout.itemsStyle().label));
            sel = null;
        }).bounds(width / 2 - 84, height / 2 + 32, 168, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Layout.save();
        super.onClose();
    }

    private List<Layout.Mod> mods() {
        List<Layout.Mod> out = new ArrayList<>();
        for (Layout.Mod m : Layout.Mod.values()) if (Layout.enabled(m)) out.add(m);
        return out;
    }

    private Layout.Mod pick(double mx, double my) {
        Layout.Mod best = null;
        float bestArea = Float.MAX_VALUE;
        for (Layout.Mod m : mods()) {
            float[] r = Layout.rect(m, width, height);
            if (mx >= r[0] - 2 && mx <= r[2] + 2 && my >= r[1] - 2 && my <= r[3] + 2) {
                float a = (r[2] - r[0]) * (r[3] - r[1]);
                if (a < bestArea) {
                    bestArea = a;
                    best = m;
                }
            }
        }
        return best;
    }

    // ---------------------------------------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, VEIL);
        Layout.Mod hover = drag == Drag.NONE ? pick(mx, my) : null;
        for (Layout.Mod m : mods()) {
            float[] r = Layout.rect(m, width, height);
            int x0 = Math.round(r[0]), y0 = Math.round(r[1]), x1 = Math.round(r[2]), y1 = Math.round(r[3]);
            boolean isSel = m == sel;
            if (!Layout.live(m)) g.fill(x0, y0, x1, y1, 0x30B59CFF);
            else if (isSel) g.fill(x0, y0, x1, y1, 0x18FFE2A8);
            int col = isSel ? GOLD : m == hover ? BOX_HOVER : BOX;
            g.renderOutline(x0, y0, x1 - x0, y1 - y0, col);
            String label = m.label + (Layout.live(m) ? "" : " (empty)") + (isSel ? "   " + Math.round(Layout.get(m).s * 100f) + "%" : "");
            int ty = y0 >= 12 ? y0 - 10 : y1 + 3;
            g.drawString(font, label, x0, ty, isSel ? GOLD : WHITE, true);
            if (isSel) {
                for (int i = 0; i < 4; i++) {
                    int cx = (i & 1) == 0 ? x0 : x1, cy = (i & 2) == 0 ? y0 : y1;
                    g.fill(cx - HANDLE, cy - HANDLE, cx + HANDLE + 1, cy + HANDLE + 1, 0xFF14101F);
                    g.fill(cx - HANDLE + 1, cy - HANDLE + 1, cx + HANDLE, cy + HANDLE, GOLD);
                }
            }
        }
        if (!Float.isNaN(guideX)) g.fill(Math.round(guideX), 0, Math.round(guideX) + 1, height, 0xAAFFE2A8);
        if (!Float.isNaN(guideY)) g.fill(0, Math.round(guideY), width, Math.round(guideY) + 1, 0xAAFFE2A8);

        String hint = "Drag = move   |   scroll or drag a corner = resize   |   arrows = nudge (Shift 10)   |   R = reset module   |   Alt = no snapping";
        int hw = font.width(hint);
        g.fill(width / 2 - hw / 2 - 6, height / 2 - 18, width / 2 + hw / 2 + 6, height / 2 - 4, 0xB0100C1C);
        g.drawString(font, hint, width / 2 - hw / 2, height / 2 - 15, WHITE, true);
        super.render(g, mx, my, pt);
    }

    // ---------------------------------------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        guideX = guideY = Float.NaN;
        if (sel != null && Layout.enabled(sel)) {
            float[] r = Layout.rect(sel, width, height);
            for (int i = 0; i < 4; i++) {
                float cx = (i & 1) == 0 ? r[0] : r[2], cy = (i & 2) == 0 ? r[1] : r[3];
                if (Math.abs(mx - cx) <= HANDLE + 1 && Math.abs(my - cy) <= HANDLE + 1) {
                    hx = (i & 1) == 0 ? -1 : 1;
                    hy = (i & 2) == 0 ? -1 : 1;
                    fx = (i & 1) == 0 ? r[2] : r[0];
                    fy = (i & 2) == 0 ? r[3] : r[1];
                    w0 = Math.max(8f, r[2] - r[0]);
                    h0 = Math.max(8f, r[3] - r[1]);
                    s0 = Layout.get(sel).s;
                    drag = Drag.SIZE;
                    return true;
                }
            }
        }
        Layout.Mod hit = pick(mx, my);
        sel = hit;
        if (hit != null) {
            drag = Drag.MOVE;
            smx = mx;
            smy = my;
            sdx = Layout.get(hit).dx;
            sdy = Layout.get(hit).dy;
            startRect = Layout.rect(hit, width, height);
        }
        return hit != null;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dxm, double dym) {
        if (button != 0 || sel == null) return super.mouseDragged(mx, my, button, dxm, dym);
        if (drag == Drag.MOVE) {
            float ddx = (float) (mx - smx), ddy = (float) (my - smy);
            guideX = guideY = Float.NaN;
            if (!hasAltDown()) {
                float[] bx = bestSnap(new float[] {startRect[0] + ddx, (startRect[0] + startRect[2]) / 2f + ddx, startRect[2] + ddx}, true);
                float[] by = bestSnap(new float[] {startRect[1] + ddy, (startRect[1] + startRect[3]) / 2f + ddy, startRect[3] + ddy}, false);
                if (!Float.isNaN(bx[0])) {
                    ddx += bx[0];
                    guideX = bx[1];
                }
                if (!Float.isNaN(by[0])) {
                    ddy += by[0];
                    guideY = by[1];
                }
            }
            // never lose the box completely off screen
            ddx = Math.max(24f - startRect[2], Math.min(width - 24f - startRect[0], ddx));
            ddy = Math.max(24f - startRect[3], Math.min(height - 24f - startRect[1], ddy));
            Layout.T t = Layout.get(sel);
            t.dx = sdx + ddx;
            t.dy = sdy + ddy;
            return true;
        }
        if (drag == Drag.SIZE) {
            float nx = (float) (mx - fx) * hx, ny = (float) (my - fy) * hy;
            float ratio = Math.max(nx / w0, ny / h0);
            Layout.setScaleKeeping(sel, s0 * Math.max(0.05f, ratio), fx, fy, width, height);
            return true;
        }
        return super.mouseDragged(mx, my, button, dxm, dym);
    }

    /** For the three moving edges (start, middle, end) finds the nearest snap target on one axis: {delta, guideCoordinate}. */
    private float[] bestSnap(float[] edges, boolean xAxis) {
        List<Float> targets = new ArrayList<>();
        float size = xAxis ? width : height;
        targets.add(0f);
        targets.add(size / 2f);
        targets.add(size);
        for (Layout.Mod m : mods()) {
            if (m == sel) continue;
            float[] r = Layout.rect(m, width, height);
            float a = xAxis ? r[0] : r[1], b = xAxis ? r[2] : r[3];
            targets.add(a);
            targets.add((a + b) / 2f);
            targets.add(b);
        }
        float bestD = SNAP + 0.5f, bestDelta = Float.NaN, guide = Float.NaN;
        for (float e : edges) {
            for (float t : targets) {
                float d = Math.abs(t - e);
                if (d < bestD) {
                    bestD = d;
                    bestDelta = t - e;
                    guide = t;
                }
            }
        }
        return new float[] {bestDelta, guide};
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        drag = Drag.NONE;
        guideX = guideY = Float.NaN;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        Layout.Mod target = pick(mx, my);
        if (target == null) target = sel;
        if (target == null || delta == 0) return super.mouseScrolled(mx, my, delta);
        sel = target;
        float[] r = Layout.rect(target, width, height);
        float f = delta > 0 ? 1.05f : 1f / 1.05f;
        Layout.setScaleKeeping(target, Layout.get(target).s * f, (r[0] + r[2]) / 2f, (r[1] + r[3]) / 2f, width, height);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (sel != null) {
            float step = (mods & GLFW.GLFW_MOD_SHIFT) != 0 ? 10f : 1f;
            Layout.T t = Layout.get(sel);
            switch (key) {
                case GLFW.GLFW_KEY_LEFT -> t.dx -= step;
                case GLFW.GLFW_KEY_RIGHT -> t.dx += step;
                case GLFW.GLFW_KEY_UP -> t.dy -= step;
                case GLFW.GLFW_KEY_DOWN -> t.dy += step;
                case GLFW.GLFW_KEY_R -> Layout.reset(sel);
                default -> {
                    return super.keyPressed(key, scan, mods);
                }
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}

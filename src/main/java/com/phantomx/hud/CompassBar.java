package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;

/**
 * Skyrim-style compass: a slim band with gold rails, the letters riding on it, tick marks, a centre notch and an ornate
 * winged end piece on each side. Gold where Skyrim has silver. Remnants Minimap markers sit on the band with their
 * distance underneath.
 */
final class CompassBar {
    private CompassBar() {}

    private static final Logger LOG = LogManager.getLogger("PhantomXHud/Compass");
    private static final String[] NAMES = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final float VIEW = 110f, BAND = 14f, MID = 7f;
    private static final int LETTER = argb(246, 214, 134, 255);

    static void render(GuiGraphics g, Font font, Player p, int sw, float scale, float partial) {
        float W = HudConfig.COMPASS_WIDTH.get();
        g.pose().pushPose();
        g.pose().translate(sw / 2f, 5f, 0);
        g.pose().scale(scale, scale, 1f);
        Matrix4f m = g.pose().last().pose();

        float half = W / 2f, ppd = W / VIEW;
        float heading = (Mth.wrapDegrees(p.getViewYRot(partial) + 180f) + 360f) % 360f;

        // translucent band, darker in the middle, clear at both ends
        int mid = withAlpha(INK, 165), edge = withAlpha(INK, 40);
        gradH(m, -half, 0, 0, BAND, edge, mid);
        gradH(m, 0, 0, half, BAND, mid, edge);

        // double gold rails: a bright outer line and a fainter inner line, brightest at the centre
        int gm = withAlpha(GOLD, 245), ge = withAlpha(GOLD, 150), im = withAlpha(GOLD, 120), ie = withAlpha(GOLD, 30);
        for (int side = -1; side <= 1; side += 2) {
            float x0 = side < 0 ? -half : 0f, x1 = side < 0 ? 0f : half;
            int l = side < 0 ? ge : gm, r = side < 0 ? gm : ge;
            gradH(m, x0, -0.5f, x1, 0.6f, l, r);
            gradH(m, x0, BAND - 0.6f, x1, BAND + 0.5f, l, r);
            int il = side < 0 ? ie : im, ir = side < 0 ? im : ie;
            gradH(m, x0, 1.9f, x1, 2.4f, il, ir);
            gradH(m, x0, BAND - 2.4f, x1, BAND - 1.9f, il, ir);
        }

        // ticks and letters
        for (int deg = 0; deg < 360; deg += 5) {
            float d = Mth.wrapDegrees(deg - heading);
            if (Math.abs(d) > VIEW / 2f) continue;
            float x = d * ppd;
            float fade = 1f - Math.abs(d) / (VIEW / 2f);
            if (deg % 45 == 0) {
                String s = NAMES[deg / 45];
                boolean cardinal = deg % 90 == 0;
                int col = cardinal ? LETTER : withAlpha(BONE, 215);
                int a = Math.round(255 * Math.min(1f, fade * 1.6f));
                if (a < 12) continue;
                float ts = cardinal ? 1.2f : 0.8f;
                g.pose().pushPose();
                g.pose().translate(x, MID - 3.5f * ts, 0);
                g.pose().scale(ts, ts, 1f);
                g.drawString(font, s, Math.round(-font.width(s) / 2f), 0, withAlpha(col, a), true);
                g.pose().popPose();
            } else {
                boolean major = deg % 15 == 0;
                float th = major ? 3.6f : 2f;
                int ta = Math.round(190 * fade);
                Matrix4f mm = g.pose().last().pose();
                line(mm, withAlpha(GOLD, ta), x, BAND - th - 0.6f, x, BAND - 0.6f, 0.6f);
                if (major) line(mm, withAlpha(GOLD, Math.round(ta * 0.7f)), x, 0.6f, x, 0.6f + th * 0.6f, 0.5f);
            }
        }

        // markers from Remnants Minimap
        if (HudConfig.COMPASS_MARKERS.get()) Markers.draw(g, font, p, heading, ppd);

        // winged end pieces
        m = g.pose().last().pose();
        ornament(m, -half, -1);
        ornament(m, half, 1);

        // centre notch: a gold pointer above the band and a small one below it
        poly(m, INK, -3.9f, -4.6f, 3.9f, -4.6f, 0f, 1.5f);
        poly(m, GOLD, -3.1f, -3.9f, 3.1f, -3.9f, 0f, 0.5f);
        poly(m, withAlpha(GOLD, 220), -2.6f, BAND + 3.4f, 2.6f, BAND + 3.4f, 0f, BAND + 0.1f);
        g.pose().popPose();
    }

    /** Ornate end piece: a gold-edged diamond with a gem and two curled filigree horns, mirrored by dir (-1 left, +1 right). */
    private static void ornament(Matrix4f m, float ex, int dir) {
        diamondFill(m, withAlpha(INK, 240), ex, MID, 5.2f, 9.4f);
        diamondLine(m, GOLD, ex, MID, 5.2f, 9.4f, 1.1f);
        diamondLine(m, withAlpha(GOLD, 150), ex, MID, 3.1f, 6.0f, 0.6f);
        diamondFill(m, GOLD, ex, MID, 1.7f, 1.7f);
        diamondFill(m, withAlpha(BONE, 200), ex, MID, 0.8f, 0.8f);
        float[][] horn = {{5.2f, 0f}, {8.6f, -1.2f}, {12.2f, -3.6f}, {15.8f, -5.2f}, {19.2f, -4.8f}, {21.4f, -2.6f}, {21f, -0.2f}, {19f, 0.6f}};
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < horn.length - 1; i++) {
                float t = i / (float) (horn.length - 1);
                float x1 = ex + dir * horn[i][0], y1 = MID + s * horn[i][1];
                float x2 = ex + dir * horn[i + 1][0], y2 = MID + s * horn[i + 1][1];
                line(m, withAlpha(GOLD, Math.round(240 - 70 * t)), x1, y1, x2, y2, 1.1f - 0.4f * t);
            }
            float lx = ex + dir * horn[horn.length - 1][0], ly = MID + s * horn[horn.length - 1][1];
            diamondFill(m, GOLD, lx, ly, 1.1f, 1.1f);
        }
        gradH(m, dir > 0 ? ex + 5.2f : ex - 24f, MID - 0.3f, dir > 0 ? ex + 24f : ex - 5.2f, MID + 0.3f,
                dir > 0 ? withAlpha(GOLD, 200) : withAlpha(GOLD, 0), dir > 0 ? withAlpha(GOLD, 0) : withAlpha(GOLD, 200));
    }

    /** Remnants Minimap markers by reflection; switches itself off after repeated failures. */
    private static final class Markers {
        private static boolean failed;
        private static int errors;
        private static Method sessionM, dimM, inDim, tint;
        private static Field markersF, hiddenF, xF, yF, zF, hasYF, kindF, nameF;
        private static boolean init;

        static void draw(GuiGraphics g, Font font, Player p, float heading, float ppd) {
            if (failed || !ModList.get().isLoaded("remnantminimaps")) return;
            try {
                if (!init) {
                    Class<?> rc = Class.forName("io.nightbeam.remnantminimaps.client.RemnantClient");
                    sessionM = rc.getMethod("session");
                    dimM = rc.getMethod("currentDimension");
                    Class<?> ms = Class.forName("io.nightbeam.remnantminimaps.client.session.MapSession");
                    markersF = ms.getField("markers");
                    Class<?> store = Class.forName("io.nightbeam.remnantminimaps.core.marker.MarkerStore");
                    inDim = store.getMethod("inDimension", String.class);
                    Class<?> mk = Class.forName("io.nightbeam.remnantminimaps.core.marker.Marker");
                    hiddenF = mk.getField("hidden");
                    xF = mk.getField("x");
                    yF = mk.getField("y");
                    zF = mk.getField("z");
                    hasYF = mk.getField("hasY");
                    kindF = mk.getField("kind");
                    nameF = mk.getField("name");
                    tint = mk.getMethod("tint");
                    init = true;
                }
                Object session = sessionM.invoke(null);
                String dim = (String) dimM.invoke(null);
                if (session == null || dim == null) return;
                Object store = markersF.get(session);
                if (store == null) return;
                List<?> list = new ArrayList<>((List<?>) inDim.invoke(store, dim));
                double range = HudConfig.COMPASS_RANGE.get(), vlim = HudConfig.COMPASS_VERTICAL.get();
                boolean deaths = HudConfig.COMPASS_DEATHS.get();
                for (Object mk : list) {
                    if (hiddenF.getBoolean(mk)) continue;
                    if (!deaths && String.valueOf(kindF.get(mk)).equals("DEATH")) continue;
                    double dx = xF.getInt(mk) + 0.5 - p.getX(), dz = zF.getInt(mk) + 0.5 - p.getZ();
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (range > 0 && dist > range) continue;
                    if (vlim > 0 && hasYF.getBoolean(mk) && Math.abs(yF.getInt(mk) - p.getY()) > vlim) continue;
                    float bearing = (float) Math.toDegrees(Math.atan2(dx, -dz));
                    float d = Mth.wrapDegrees(bearing - heading);
                    if (Math.abs(d) > VIEW / 2f - 3f) continue;
                    float x = d * ppd;
                    int rgb = ((Integer) tint.invoke(mk)) & 0xFFFFFF;
                    int col = 0xFF000000 | rgb;
                    Matrix4f m = g.pose().last().pose();
                    diamondFill(m, INK, x, MID, 4.6f, 4.6f);
                    diamondFill(m, col, x, MID, 3.4f, 3.4f);
                    diamondLine(m, withAlpha(GOLD, 230), x, MID, 4.6f, 4.6f, 0.7f);
                    String s = dist >= 1000 ? String.format("%.1fk", dist / 1000.0) : Math.round(dist) + "m";
                    g.pose().pushPose();
                    g.pose().translate(x, BAND + 5.2f, 0);
                    g.pose().scale(0.55f, 0.55f, 1f);
                    g.drawString(font, s, Math.round(-font.width(s) / 2f), 0, BONE, true);
                    g.pose().popPose();
                }
                errors = 0;
            } catch (Throwable t) {
                if (++errors >= 5) {
                    failed = true;
                    LOG.warn("Remnants markers disabled on the compass: {}", t.toString());
                }
            }
        }
    }
}

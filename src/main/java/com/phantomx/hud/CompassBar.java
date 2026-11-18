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
 * Compass in the Skyrim manner, sharper and in gold: a solid dark bar with a fine gold double rail, the letters and
 * ticks running along it, and a pointed gold crest on each end. Remnants Minimap markers sit on the bar with their
 * distance underneath.
 */
final class CompassBar {
    private CompassBar() {}

    private static final Logger LOG = LogManager.getLogger("PhantomXHud/Compass");
    private static final String[] NAMES = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final float VIEW = 110f, BAND = 14f, MID = 7f;
    private static final int LETTER = argb(246, 214, 134, 255), GOLD_HI = argb(246, 214, 134, 255);

    static void render(GuiGraphics g, Font font, Player p, int sw, float scale, float partial) {
        float W = HudConfig.COMPASS_WIDTH.get();
        g.pose().pushPose();
        g.pose().translate(sw / 2f, 5f, 0);
        g.pose().scale(scale, scale, 1f);
        Matrix4f m = g.pose().last().pose();

        float half = W / 2f, ppd = W / VIEW;
        float heading = (Mth.wrapDegrees(p.getViewYRot(partial) + 180f) + 360f) % 360f;

        // solid bar
        rect(m, withAlpha(INK, 238), -half, 0f, half, BAND);
        gradH(m, -half, 0f, 0f, BAND, withAlpha(argb(40, 34, 60, 255), 0), withAlpha(argb(40, 34, 60, 255), 120));
        gradH(m, 0f, 0f, half, BAND, withAlpha(argb(40, 34, 60, 255), 120), withAlpha(argb(40, 34, 60, 255), 0));

        // gold double rail, brighter towards the middle: a solid outer line and a fine inner one
        gradH(m, -half, -0.5f, 0f, 0.7f, GOLD, GOLD_HI);
        gradH(m, 0f, -0.5f, half, 0.7f, GOLD_HI, GOLD);
        gradH(m, -half, BAND - 0.7f, 0f, BAND + 0.5f, GOLD, GOLD_HI);
        gradH(m, 0f, BAND - 0.7f, half, BAND + 0.5f, GOLD_HI, GOLD);
        rect(m, withAlpha(GOLD, 105), -half, 2.1f, half, 2.55f);
        rect(m, withAlpha(GOLD, 105), -half, BAND - 2.55f, half, BAND - 2.1f);

        // ticks and letters (they fade out under the crests)
        for (int deg = 0; deg < 360; deg += 5) {
            float d = Mth.wrapDegrees(deg - heading);
            if (Math.abs(d) > VIEW / 2f) continue;
            float x = d * ppd;
            float edgeFade = PlayerBlock.clamp01((half - Math.abs(x) - 3f) / 14f);
            if (edgeFade <= 0.02f) continue;
            if (deg % 45 == 0) {
                String s = NAMES[deg / 45];
                boolean cardinal = deg % 90 == 0;
                int col = cardinal ? LETTER : withAlpha(BONE, 220);
                float ts = cardinal ? 1.05f : 0.72f;
                g.pose().pushPose();
                g.pose().translate(x, MID - 3.5f * ts, 0);
                g.pose().scale(ts, ts, 1f);
                g.drawString(font, s, Math.round(-font.width(s) / 2f), 0, withAlpha(col, Math.round(255 * edgeFade)), true);
                g.pose().popPose();
            } else {
                boolean major = deg % 15 == 0;
                float th = major ? 3.2f : 1.7f;
                int ta = Math.round(200 * edgeFade);
                Matrix4f mm = g.pose().last().pose();
                line(mm, withAlpha(GOLD, ta), x, BAND - th - 0.7f, x, BAND - 0.7f, 0.6f);
                if (major) line(mm, withAlpha(GOLD, Math.round(ta * 0.6f)), x, 0.7f, x, 0.7f + th * 0.55f, 0.5f);
            }
        }

        // markers from Remnants Minimap
        if (HudConfig.COMPASS_MARKERS.get()) Markers.draw(g, font, p, heading, ppd);

        m = g.pose().last().pose();
        crest(m, -half, -1);
        crest(m, half, 1);

        // centre pointer
        poly(m, INK, -3.4f, -4.2f, 3.4f, -4.2f, 0f, 1.2f);
        poly(m, GOLD_HI, -2.6f, -3.5f, 2.6f, -3.5f, 0f, 0.3f);
        g.pose().popPose();
    }

    /** Pointed gold crest on one end (dir -1 left, +1 right): taller than the bar, a sharp tip, a clean inner outline and a gem. */
    private static void crest(Matrix4f m, float ex, int dir) {
        float rise = 3.4f, j = ex - dir * 1.2f, sx = ex + dir * 7f, tip = ex + dir * 19f, bot = BAND + rise;
        float[] o = {j, -rise, sx, -rise, tip, MID, sx, bot, j, bot};
        poly(m, withAlpha(INK, 250), o);
        for (int i = 0; i < o.length / 2; i++) {
            int n = (i + 1) % (o.length / 2);
            line(m, GOLD_HI, o[i * 2], o[i * 2 + 1], o[n * 2], o[n * 2 + 1], 1.15f);
        }
        // inner outline: the same shape pulled towards its centre
        float cx = ex + dir * 8f, k = 0.66f;
        for (int i = 0; i < o.length / 2; i++) {
            int n = (i + 1) % (o.length / 2);
            float x1 = cx + (o[i * 2] - cx) * k, y1 = MID + (o[i * 2 + 1] - MID) * k, x2 = cx + (o[n * 2] - cx) * k, y2 = MID + (o[n * 2 + 1] - MID) * k;
            line(m, withAlpha(GOLD, 150), x1, y1, x2, y2, 0.55f);
        }
        diamondLine(m, GOLD, cx, MID, 3.6f, 3.6f, 0.9f);
        diamondFill(m, GOLD_HI, cx, MID, 1.5f, 1.5f);
        line(m, withAlpha(GOLD, 200), cx - dir * 3.6f, MID, cx - dir * 6.2f, MID, 0.6f);
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

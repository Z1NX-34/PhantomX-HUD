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

/** Thin compass at the top: fading band, gold hairline, cardinal letters and Remnants Minimap markers. */
final class CompassBar {
    private CompassBar() {}

    private static final Logger LOG = LogManager.getLogger("PhantomXHud/Compass");
    private static final String[] NAMES = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private static final float VIEW = 110f;

    static void render(GuiGraphics g, Font font, Player p, int sw, float scale, float partial) {
        float W = HudConfig.COMPASS_WIDTH.get();
        g.pose().pushPose();
        g.pose().translate(sw / 2f, 5f, 0);
        g.pose().scale(scale, scale, 1f);
        Matrix4f m = g.pose().last().pose();

        float half = W / 2f, bandH = 12f, ppd = W / VIEW;
        float heading = (Mth.wrapDegrees(p.getViewYRot(partial) + 180f) + 360f) % 360f;

        // band and hairlines, fading to nothing at both ends
        int mid = withAlpha(INK, 150), edge = withAlpha(INK, 0);
        gradH(m, -half, 0, 0, bandH, edge, mid);
        gradH(m, 0, 0, half, bandH, mid, edge);
        int gm = withAlpha(GOLD, 230), ge = withAlpha(GOLD, 0);
        gradH(m, -half, bandH, 0, bandH + 0.7f, ge, gm);
        gradH(m, 0, bandH, half, bandH + 0.7f, gm, ge);
        gradH(m, -half * 0.8f, -0.7f, 0, 0f, ge, withAlpha(GOLD, 120));
        gradH(m, 0, -0.7f, half * 0.8f, 0f, withAlpha(GOLD, 120), ge);

        // ticks and letters
        for (int deg = 0; deg < 360; deg += 5) {
            float d = Mth.wrapDegrees(deg - heading);
            if (Math.abs(d) > VIEW / 2f) continue;
            float x = d * ppd;
            float fade = 1f - Math.abs(d) / (VIEW / 2f);
            if (deg % 45 == 0) {
                String s = NAMES[deg / 45];
                boolean cardinal = deg % 90 == 0;
                int col = cardinal ? (deg == 0 ? RED : GOLD) : withAlpha(BONE, 200);
                int a = Math.round(255 * Math.min(1f, fade * 1.6f));
                if (a < 12) continue;
                g.pose().pushPose();
                g.pose().translate(x, cardinal ? 2.6f : 3.4f, 0);
                float ts = cardinal ? 1f : 0.75f;
                g.pose().scale(ts, ts, 1f);
                g.drawString(font, s, Math.round(-font.width(s) / 2f), 0, withAlpha(col, a), true);
                g.pose().popPose();
            } else {
                float th = deg % 15 == 0 ? 3.5f : 1.8f;
                line(g.pose().last().pose(), withAlpha(GOLD, Math.round(170 * fade)), x, bandH - th, x, bandH, 0.6f);
            }
        }

        // markers from Remnants Minimap
        if (HudConfig.COMPASS_MARKERS.get()) Markers.draw(g, font, p, heading, ppd);

        // centre pointer and heading
        m = g.pose().last().pose();
        poly(m, GOLD, -3.2f, -3.6f, 3.2f, -3.6f, 0f, 0.6f);
        g.pose().pushPose();
        g.pose().translate(0, bandH + 2.5f, 0);
        g.pose().scale(0.6f, 0.6f, 1f);
        String hs = Math.round(heading) % 360 + "°";
        g.drawString(font, hs, Math.round(-font.width(hs) / 2f), 0, withAlpha(BONE, 200), true);
        g.pose().popPose();
        g.pose().popPose();
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
                    diamondFill(m, INK, x, 6f, 4.3f, 4.3f);
                    diamondFill(m, col, x, 6f, 3.2f, 3.2f);
                    diamondLine(m, withAlpha(GOLD, 220), x, 6f, 4.3f, 4.3f, 0.6f);
                    String s = dist >= 1000 ? String.format("%.1fk", dist / 1000.0) : Math.round(dist) + "m";
                    g.pose().pushPose();
                    g.pose().translate(x, 13.5f, 0);
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

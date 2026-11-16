package com.phantomx.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * User layout: every module of the HUD can be moved and resized. A module's saved transform is an offset (GUI pixels) and
 * a size multiplier applied about the module's own anchor point, so the default (no file, or "reset") is exactly the
 * normal PhantomX layout. Saved to config/phantomx_hud-layout.json. The edit screen is LayoutScreen.
 */
final class Layout {
    private Layout() {}

    private static final Logger LOG = LogManager.getLogger("PhantomXHud/Layout");

    static final KeyMapping KEY = new KeyMapping("key.phantomxhud.edit", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, "key.categories.phantomxhud");
    /** Set by the /phantomxhud edit command; the screen opens on the next client tick (the chat closes after a command). */
    static boolean openRequested;

    enum Mod {
        PLAYER("Player"), SKILLS("Skills"), ITEMS("Items & XP"), EFFECTS("Effects"), COMPASS("Compass"), BOSS("Boss bars"), MOUNT("Mount bar");

        final String label;

        Mod(String label) {
            this.label = label;
        }
    }

    static final class T {
        float dx, dy, s = 1f;

        boolean identity() {
            return Math.abs(dx) < 0.001f && Math.abs(dy) < 0.001f && Math.abs(s - 1f) < 0.0001f;
        }
    }

    /** Anchor (px,py), size unit u and the default rectangle of the module relative to the anchor (in units of u). */
    record Frame(float px, float py, float u, float x0, float y0, float x1, float y1) {}

    private static final Map<Mod, T> MAP = new EnumMap<>(Mod.class);
    private static boolean loaded;

    static final float MIN_S = 0.4f, MAX_S = 3f;

    // ---------------------------------------------------------------------------------------------- state

    static T get(Mod m) {
        if (!loaded) load();
        return MAP.computeIfAbsent(m, k -> new T());
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("phantomx_hud-layout.json");
    }

    static void load() {
        loaded = true;
        try {
            Path p = file();
            if (!Files.exists(p)) return;
            JsonObject root = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            JsonObject mods = root.getAsJsonObject("modules");
            if (mods == null) return;
            for (Mod m : Mod.values()) {
                if (!mods.has(m.name())) continue;
                JsonObject j = mods.getAsJsonObject(m.name());
                T t = new T();
                t.dx = j.has("dx") ? j.get("dx").getAsFloat() : 0f;
                t.dy = j.has("dy") ? j.get("dy").getAsFloat() : 0f;
                t.s = clamp(j.has("s") ? j.get("s").getAsFloat() : 1f, MIN_S, MAX_S);
                MAP.put(m, t);
            }
        } catch (Throwable t) {
            LOG.warn("Could not read the layout file, using the default layout: {}", t.toString());
        }
    }

    static void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            JsonObject mods = new JsonObject();
            for (Mod m : Mod.values()) {
                T t = get(m);
                if (t.identity()) continue;
                JsonObject j = new JsonObject();
                j.addProperty("dx", Math.round(t.dx * 100f) / 100f);
                j.addProperty("dy", Math.round(t.dy * 100f) / 100f);
                j.addProperty("s", Math.round(t.s * 1000f) / 1000f);
                mods.add(m.name(), j);
            }
            root.add("modules", mods);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(file(), gson.toJson(root));
        } catch (Throwable t) {
            LOG.warn("Could not save the layout file: {}", t.toString());
        }
    }

    static void reset(Mod m) {
        MAP.put(m, new T());
    }

    static void resetAll() {
        for (Mod m : Mod.values()) reset(m);
        save();
    }

    static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---------------------------------------------------------------------------------------------- geometry

    static boolean enabled(Mod m) {
        return switch (m) {
            case PLAYER -> true;
            case SKILLS -> HudConfig.SHOW_SKILLS.get();
            case ITEMS -> HudConfig.SHOW_HOTBAR.get() || HudConfig.SHOW_XP.get();
            case EFFECTS -> HudConfig.SHOW_EFFECTS.get();
            case COMPASS -> HudConfig.SHOW_COMPASS.get();
            case BOSS -> HudConfig.SHOW_BOSS.get();
            case MOUNT -> HudConfig.SHOW_MOUNT.get();
        };
    }

    /** True when the module currently has something to draw (otherwise the edit screen shows an empty placeholder). */
    static boolean live(Mod m) {
        Player p = Minecraft.getInstance().player;
        if (p == null) return false;
        return switch (m) {
            case PLAYER, ITEMS, COMPASS -> true;
            case SKILLS -> SkillRow.count() > 0;
            case EFFECTS -> !p.getActiveEffects().isEmpty();
            case BOSS -> BossBars.count() > 0;
            case MOUNT -> p.getVehicle() instanceof LivingEntity;
        };
    }

    // geometry of the diamond module (see DiamondHotbar): extents of the bottom-right module around its centre
    private static final float ITEMS_EXT_R = 3f * 21f + 2f * 1.6f, ITEMS_EXT_B = 21f + 1.6f + 21f + 6.5f;

    static Frame frame(Mod m, int sw, int sh) {
        float s = HudConfig.SCALE.get().floatValue();
        Player p = Minecraft.getInstance().player;
        switch (m) {
            case PLAYER:
                return new Frame(HudConfig.MARGIN_X.get(), sh - HudConfig.MARGIN_Y.get(), s, -6f, -84f, 226f, 6f);
            case SKILLS: {
                float crestTop = PlayerBlock.crestTopScreenY(sh, s);
                float left = HudConfig.MARGIN_X.get() + s * (PlayerBlock.CX + 4f);
                float cy = crestTop - HudConfig.SKILLS_Y.get() * s - 12f * s;
                int n = Math.max(3, SkillRow.count());
                return new Frame(left, cy, s, -1f, -13f, n * 25.5f, 13f);
            }
            case ITEMS: {
                float ms = s * HudConfig.MODULE_SCALE.get().floatValue();
                return new Frame(sw - ms * HudConfig.MODULE_MARGIN_X.get(), sh - ms * HudConfig.MODULE_MARGIN_Y.get(), ms,
                        -(ITEMS_EXT_R + 42f), -(ITEMS_EXT_B + 54f), 2f, 2f);
            }
            case EFFECTS: {
                int n = p == null || p.getActiveEffects().isEmpty() ? 3 : Math.min(28, p.getActiveEffects().size());
                int cols = (n + 6) / 7, rows = Math.min(7, n);
                float h = (rows * 22.4f - 2.4f) / 2f + 2f;
                return new Frame(sw - 4f * s, sh / 2f + HudConfig.EFFECTS_OFFSET_Y.get() * s, s, -((cols - 1) * 73.2f + 66f), -h, 1f, h);
            }
            case COMPASS: {
                float half = HudConfig.COMPASS_WIDTH.get() / 2f + 6f;
                return new Frame(sw / 2f, 5f, s, -half, -5f, half, 22f);
            }
            case BOSS: {
                int k = Math.max(1, Math.min(BossBars.count(), HudConfig.BOSS_MAX.get()));
                float w = Math.min(270f, sw / s * 0.42f);
                return new Frame(sw / 2f, sh - 92f * s, s, -w / 2f - 5f, -14f - (k - 1) * 34f, w / 2f + 5f, 10f);
            }
            default: {
                float lift = p != null ? DragonBars.mountLift(p.getVehicle()) : 0f;
                return new Frame(sw / 2f, sh - 33f - lift * s, s, -82f, -15f, 80f, 3f);
            }
        }
    }

    /** The module's rectangle on screen with its saved transform applied: {x0, y0, x1, y1}. */
    static float[] rect(Mod m, int sw, int sh) {
        Frame f = frame(m, sw, sh);
        T t = get(m);
        float ax = f.px() + f.u() * f.x0(), ay = f.py() + f.u() * f.y0(), bx = f.px() + f.u() * f.x1(), by = f.py() + f.u() * f.y1();
        return new float[] {map(f, t, ax, true), map(f, t, ay, false), map(f, t, bx, true), map(f, t, by, false)};
    }

    private static float map(Frame f, T t, float v, boolean x) {
        float p = x ? f.px() : f.py();
        return p + (x ? t.dx : t.dy) + t.s * (v - p);
    }

    static float mapX(Mod m, int sw, int sh, float x) {
        Frame f = frame(m, sw, sh);
        return map(f, get(m), x, true);
    }

    static float mapY(Mod m, int sw, int sh, float y) {
        Frame f = frame(m, sw, sh);
        return map(f, get(m), y, false);
    }

    static float scaleOf(Mod m) {
        return get(m).s;
    }

    /** Changes the size multiplier while the screen point (fx, fy) stays where it is. */
    static void setScaleKeeping(Mod m, float ns, float fx, float fy, int sw, int sh) {
        Frame f = frame(m, sw, sh);
        T t = get(m);
        float dxd = f.px() + (fx - f.px() - t.dx) / t.s;        // the point in default (untransformed) space
        float dyd = f.py() + (fy - f.py() - t.dy) / t.s;
        t.s = clamp(ns, MIN_S, MAX_S);
        t.dx = fx - f.px() - t.s * (dxd - f.px());
        t.dy = fy - f.py() - t.s * (dyd - f.py());
    }

    // ---------------------------------------------------------------------------------------------- drawing

    static void begin(GuiGraphics g, Mod m, int sw, int sh) {
        T t = get(m);
        g.pose().pushPose();
        if (t.identity()) return;
        Frame f = frame(m, sw, sh);
        g.pose().translate(f.px() + t.dx, f.py() + t.dy, 0f);
        g.pose().scale(t.s, t.s, 1f);
        g.pose().translate(-f.px(), -f.py(), 0f);
    }

    static void end(GuiGraphics g) {
        g.pose().popPose();
    }

    static void draw(GuiGraphics g, Mod m, int sw, int sh, Runnable body) {
        begin(g, m, sw, sh);
        try {
            body.run();
        } finally {
            end(g);
        }
    }
}

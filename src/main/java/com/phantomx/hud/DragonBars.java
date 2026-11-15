package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Saint's Dragons draws its own ride health bar (the 182x22 spiky bar with the "70/460" text, at sh-57) from a
 * RenderGuiOverlayEvent.Post handler, not from a registered overlay, so it cannot be cancelled or hidden by overlay id.
 * Its flame / breath meter sits directly below it (from sh-35) and is kept.
 *
 * While you ride one of those dragons a depth-only occluder is placed over exactly the health bar's rectangle just
 * before the mod's handler runs and removed right after, so the bar (and its text) never reach the screen and nothing
 * else is touched. Soft dependency: the dragon class is matched by name only.
 */
final class DragonBars {
    private DragonBars() {}

    private static final String BASE = "com.leon.saintsdragons.server.entity.base.DragonEntity";
    private static final Map<Class<?>, Boolean> IS_DRAGON = new HashMap<>();
    private static boolean active, depthWas;

    static boolean isDragon(Entity e) {
        if (e == null) return false;
        return IS_DRAGON.computeIfAbsent(e.getClass(), c -> {
            for (Class<?> k = c; k != null; k = k.getSuperclass()) {
                if (BASE.equals(k.getName())) return true;
            }
            return false;
        });
    }

    /** Cindervane and Varasuchus draw their bar 6 px lower and have no meter under it. */
    private static boolean lowBar(Entity e) {
        for (Class<?> k = e.getClass(); k != null; k = k.getSuperclass()) {
            String n = k.getSimpleName();
            if (n.equals("Cindervane") || n.equals("Varasuchus")) return true;
        }
        return false;
    }

    static boolean wanted() {
        if (!HudConfig.ENABLED.get() || !HudConfig.SHOW_MOUNT.get()) return false;
        Player p = Minecraft.getInstance().player;
        return p != null && isDragon(p.getVehicle());
    }

    /** Extra lift for the mount bar so it clears the dragon's own meter underneath it. */
    static float mountLift(Entity mount) {
        return isDragon(mount) ? 8f : 0f;
    }

    static void begin(GuiGraphics g, int sw, int sh) {
        Player p = Minecraft.getInstance().player;
        if (active || p == null) return;
        float x0 = (sw - 182) / 2f - 2f, x1 = x0 + 186f;
        float y0 = sh - 60f, y1 = sh - 35f;
        if (lowBar(p.getVehicle())) {
            y0 += 6f;
            y1 += 6f;
        }
        Matrix4f m = g.pose().last().pose();
        g.flush();
        depthWas = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        RenderSystem.enableDepthTest();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.colorMask(false, false, false, false);
        try {
            polyZ(m, WHITE, 300f, x0, y0, x1, y0, x1, y1, x0, y1);
        } finally {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        }
        active = true;
    }

    static void end(GuiGraphics g) {
        if (!active) return;
        active = false;
        g.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        if (!depthWas) RenderSystem.disableDepthTest();
    }
}

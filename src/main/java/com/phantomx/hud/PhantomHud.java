package com.phantomx.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

/** PhantomX HUD: diamond crest with live player model, angled bars, centred XP bar, effect strip. */
public final class PhantomHud {
    private PhantomHud() {}

    private static final Logger LOG = LogManager.getLogger("PhantomXHud");

    private static final int INK = HudDraw.argb(14, 12, 14, 255);
    private static final int BONE = HudDraw.argb(236, 228, 212, 255);
    private static final int RED = HudDraw.argb(196, 32, 38, 255);
    private static final int GOLD = HudDraw.argb(214, 174, 84, 255);
    private static final int PURP = HudDraw.argb(70, 56, 108, 255);
    private static final int CREAM = HudDraw.argb(214, 206, 188, 255);
    private static final int BLUE = HudDraw.argb(120, 150, 190, 255);
    private static final int WHITE = 0xFFFFFFFF;

    private static final float R = 28f;
    private static final float HEALTH_LEN = 130f, STAMINA_LEN = 105f, MANA_LEN = 80f;

    private static float blockTx, blockTy, blockScale = 1f;
    private static float trailHealth = -1f;
    private static long lastNanos;

    @Mod.EventBusSubscriber(modid = PhantomXHud.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void register(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("phantomx_hud", (gui, g, partial, w, h) -> {
                try {
                    if (HudConfig.ENABLED.get()) render(g, partial, w, h);
                } catch (Throwable t) {
                    LOG.warn("render error: {}", t.toString());
                }
            });
        }
    }

    @Mod.EventBusSubscriber(modid = PhantomXHud.MODID, value = Dist.CLIENT)
    public static final class ForgeBus {
        private ForgeBus() {}

        private static final ResourceLocation SCH_GAUGE = new ResourceLocation("souls_combat_hud", "player_gauge_overlay");
        private static final ResourceLocation SCH_XP = new ResourceLocation("souls_combat_hud", "experience_overlay");

        @SubscribeEvent
        public static void hideOthers(RenderGuiOverlayEvent.Pre e) {
            if (!HudConfig.ENABLED.get()) return;
            ResourceLocation id = e.getOverlay().id();
            boolean hide = id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                    || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
                    || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                    || (HudConfig.SHOW_XP.get() && id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id()))
                    || (HudConfig.SHOW_EFFECTS.get() && id.equals(VanillaGuiOverlay.POTION_ICONS.id()));
            if (!hide && HudConfig.HIDE_OTHER_HUDS.get()) hide = id.equals(SCH_GAUGE) || id.equals(SCH_XP);
            if (hide) e.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------------------------------------- render

    private static void render(GuiGraphics g, float partial, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.options.hideGui || p.isSpectator()) return;
        Font font = mc.font;
        float scale = HudConfig.SCALE.get().floatValue();

        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;

        blockTx = HudConfig.MARGIN_X.get();
        blockTy = sh - HudConfig.MARGIN_Y.get();
        blockScale = scale;
        g.pose().pushPose();
        g.pose().translate(blockTx, blockTy, 0);
        g.pose().scale(scale, scale, 1f);
        drawPlayerBlock(g, font, p, R + 5f, -(R + 16f), dt);
        g.pose().popPose();

        if (HudConfig.SHOW_XP.get()) drawXp(g, font, p, sw, sh, scale);
        if (HudConfig.SHOW_EFFECTS.get()) drawEffects(g, font, p, sw, sh, scale);
    }

    private static void drawPlayerBlock(GuiGraphics g, Font font, Player p, float cx, float cy, float dt) {
        drawModel(g, p, cx, cy);
        Matrix4f m = g.pose().last().pose();

        HudDraw.diamondLine(m, GOLD, cx, cy, R, R, 1.4f);
        HudDraw.diamondLine(m, HudDraw.withAlpha(GOLD, 110), cx, cy, R + 3f, R + 3f, 0.6f);
        HudDraw.diamondLine(m, HudDraw.withAlpha(PURP, 190), cx, cy, R - 4f, R - 4f, 0.6f);
        float[][] studs = {{cx, cy - R - 3f}, {cx + R + 3f, cy}, {cx, cy + R + 3f}, {cx - R - 3f, cy}};
        for (float[] s : studs) {
            HudDraw.diamondFill(m, INK, s[0], s[1], 2.6f, 2.6f);
            HudDraw.diamondFill(m, GOLD, s[0], s[1], 1.9f, 1.9f);
        }

        float hp = clamp01(p.getHealth() / Math.max(1f, p.getMaxHealth()));
        if (trailHealth < 0f || hp >= trailHealth) trailHealth = hp;
        else trailHealth = Math.max(hp, trailHealth - dt * 0.35f);

        int hpCol = p.hasEffect(MobEffects.WITHER) ? HudDraw.argb(60, 52, 56, 255)
                : p.hasEffect(MobEffects.POISON) ? HudDraw.argb(110, 150, 40, 255) : RED;
        float healthY = cy - R * 0.62f + 2.0f;
        float xe = angledBar(g, cx, cy, true, healthY, 8f, HEALTH_LEN, hp, hpCol, trailHealth);
        if (p.getAbsorptionAmount() > 0f) {
            float ab = clamp01(p.getAbsorptionAmount() / Math.max(1f, p.getMaxHealth()));
            Matrix4f m2 = g.pose().last().pose();
            float x0 = edge(cx, cy, true, healthY) + 5f;
            HudDraw.line(m2, HudDraw.argb(255, 214, 110, 255), x0 + 3f, healthY + 9.6f, x0 + 3f + HEALTH_LEN * ab, healthY + 9.6f, 1.2f);
        }
        g.drawString(font, String.valueOf(Math.round(p.getHealth())), Math.round(xe + 4f), Math.round(healthY), WHITE, true);

        float st = ResourceSources.stamina();
        if (st >= 0f) angledBar(g, cx, cy, false, cy + R * 0.20f, 5.5f, STAMINA_LEN, st, CREAM, -1f);
        float mn = ResourceSources.mana();
        if (mn >= 0f) angledBar(g, cx, cy, false, cy + R * 0.20f + 8f, 4.5f, MANA_LEN, mn, BLUE, -1f);

        if (HudConfig.SHOW_CHIPS.get()) {
            chip(g, font, cx - 18f, cy + R + 12f, String.valueOf(p.getArmorValue()));
            chip(g, font, cx + 12f, cy + R + 12f, String.valueOf(p.getFoodData().getFoodLevel()));
        }
    }

    private static void chip(GuiGraphics g, Font font, float x, float y, String text) {
        Matrix4f m = g.pose().last().pose();
        HudDraw.diamondFill(m, HudDraw.withAlpha(INK, 230), x, y, 4f, 4f);
        HudDraw.diamondLine(m, HudDraw.withAlpha(GOLD, 220), x, y, 4f, 4f, 0.7f);
        g.drawString(font, text, Math.round(x + 7f), Math.round(y - 4f), BONE, true);
    }

    private static float angledBar(GuiGraphics g, float cx, float cy, boolean upper, float y0, float h, float length, float pct, int col, float trail) {
        Matrix4f m = g.pose().last().pose();
        float y1 = y0 + h, gap = 5f, sl = 4.5f;
        float xTop = edge(cx, cy, upper, y0) + gap, xBot = edge(cx, cy, upper, y1) + gap;
        float x0 = Math.max(xTop, xBot), xe = x0 + length;
        HudDraw.poly(m, HudDraw.withAlpha(INK, 220), xTop, y0, xe, y0, xe - sl, y1, xBot, y1);
        float ins = 1f;
        if (trail > pct) {
            float xt = x0 + length * trail;
            HudDraw.poly(m, HudDraw.withAlpha(BONE, 235), xTop + ins, y0 + ins, xt - ins, y0 + ins, xt - ins - sl, y1 - ins, xBot + ins, y1 - ins);
        }
        float xf = x0 + length * clamp01(pct);
        if (pct > 0.001f) HudDraw.poly(m, col, xTop + ins, y0 + ins, xf - ins, y0 + ins, xf - ins - sl, y1 - ins, xBot + ins, y1 - ins);
        HudDraw.line(m, HudDraw.withAlpha(GOLD, 190), xTop + 1f, y0, xe, y0, 0.6f);
        return xe;
    }

    private static float edge(float cx, float cy, boolean upper, float y) {
        return upper ? cx + (y - (cy - R)) : cx + R - (y - cy);
    }

    // ---------------------------------------------------------------------------------------------- model

    private static void drawModel(GuiGraphics g, Player p, float cx, float cy) {
        if (!(p instanceof AbstractClientPlayer acp)) return;
        Minecraft mc = Minecraft.getInstance();
        boolean stencil = false;
        try {
            stencil = mc.getMainRenderTarget().isStencilEnabled();
            if (stencil) {
                g.flush();
                RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, Minecraft.ON_OSX);
                GL11.glEnable(GL11.GL_STENCIL_TEST);
                RenderSystem.stencilMask(0xFF);
                RenderSystem.stencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
                RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
                RenderSystem.colorMask(false, false, false, false);
                HudDraw.diamondFill(g.pose().last().pose(), WHITE, cx, cy, R - 2f, R - 2f);
                g.flush();
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.stencilMask(0x00);
                RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            }
        } catch (Throwable t) {
            stencil = false;
        }
        g.pose().pushPose();
        g.pose().setIdentity();
        try {
            float ax = blockTx + blockScale * cx, ay = blockTy + blockScale * cy, ar = blockScale * R;
            int boxX = Math.round(ax - ar), boxY = Math.round(ay - ar), box = Math.round(ar * 2f);
            int msize = Math.round(ar * 1.75f);
            int mx = Math.round(ax - msize / 2f), my = Math.round(ay + ar * 2.3f - msize * 1.6f);
            if (!previewRenderer(g, acp, boxX, boxY, box, mx, my, msize))
                vanillaModel(g, acp, Math.round(ax), Math.round(ay + ar * 2.3f), Math.round(msize * 0.9f));
        } finally {
            g.pose().popPose();
            if (stencil) {
                g.flush();
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                RenderSystem.stencilMask(0xFF);
                RenderSystem.stencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
            }
        }
    }

    private static Method preview;
    private static boolean previewFailed;

    /** Optional: the Souls Combat HUD library's preview renderer, when present, keeps Epic Fight poses correct. */
    private static boolean previewRenderer(GuiGraphics g, AbstractClientPlayer p, int x, int y, int box, int mx, int my, int msize) {
        if (previewFailed) return false;
        try {
            if (preview == null) {
                Class<?> c = Class.forName("net.tablesouls.ts_ui_lib.client.preview.PlayerModelPreviewRenderer");
                preview = c.getMethod("render", GuiGraphics.class, AbstractClientPlayer.class,
                        int.class, int.class, int.class, int.class, int.class, int.class, float.class);
            }
            Object ok = preview.invoke(null, g, p, x, y, box, mx, my, msize, 340f);
            return ok instanceof Boolean b && b;
        } catch (ClassNotFoundException e) {
            previewFailed = true;
            return false;
        } catch (Throwable t) {
            previewFailed = true;
            LOG.warn("preview renderer failed, using vanilla model: {}", t.toString());
            return false;
        }
    }

    private static void vanillaModel(GuiGraphics g, AbstractClientPlayer p, int x, int y, int scale) {
        float bodyRot = p.yBodyRot, bodyRotO = p.yBodyRotO, headRot = p.yHeadRot, headRotO = p.yHeadRotO;
        float yRot = p.getYRot(), yRotO = p.yRotO, xRot = p.getXRot(), xRotO = p.xRotO;
        try {
            float face = 180f + 25f;
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

    // ---------------------------------------------------------------------------------------------- XP

    private static void drawXp(GuiGraphics g, Font font, Player p, int sw, int sh, float scale) {
        g.pose().pushPose();
        g.pose().translate(sw / 2f, sh - 22f - 11f, 0);
        g.pose().scale(scale, scale, 1f);
        Matrix4f m = g.pose().last().pose();
        float w = 105f, h = 2.8f, sl = 2.5f, x0 = -w / 2f;
        HudDraw.poly(m, HudDraw.withAlpha(INK, 225), x0 + sl, 0, x0 + w, 0, x0 + w - sl, h, x0, h);
        float fill = (w - 1f) * clamp01(p.experienceProgress);
        if (fill > 0.5f) HudDraw.poly(m, HudDraw.withAlpha(GOLD, 240), x0 + sl + 0.5f, 0.5f, x0 + 0.5f + fill, 0.5f, x0 + fill - sl + 0.5f, h - 0.5f, x0 + 0.5f, h - 0.5f);
        HudDraw.line(m, HudDraw.withAlpha(GOLD, 150), x0 + sl, 0, x0 + w, 0, 0.5f);
        HudDraw.diamondFill(m, INK, 0, h / 2f, 6.5f, 5.5f);
        HudDraw.diamondLine(m, GOLD, 0, h / 2f, 6.5f, 5.5f, 0.7f);
        String lvl = String.valueOf(p.experienceLevel);
        g.drawString(font, lvl, Math.round(-font.width(lvl) / 2f), Math.round(h / 2f - 4f), BONE, true);
        g.pose().popPose();
    }

    // ---------------------------------------------------------------------------------------------- effects

    private static void drawEffects(GuiGraphics g, Font font, Player p, int sw, int sh, float scale) {
        Collection<MobEffectInstance> all = p.getActiveEffects();
        if (all.isEmpty()) return;
        List<MobEffectInstance> list = new ArrayList<>(all);
        Minecraft mc = Minecraft.getInstance();
        g.pose().pushPose();
        g.pose().translate(sw - 12f, sh - HudConfig.EFFECTS_Y.get(), 0);
        g.pose().scale(scale, scale, 1f);
        float size = 18f, gapX = 4f;
        for (int i = 0; i < list.size() && i < 8; i++) {
            MobEffectInstance e = list.get(i);
            float x = -size - i * (size + gapX);
            Matrix4f m = g.pose().last().pose();
            boolean good = e.getEffect().isBeneficial();
            HudDraw.poly(m, HudDraw.withAlpha(INK, 215), x, 0, x + size, 0, x + size, size + 9f, x, size + 9f);
            int border = good ? HudDraw.withAlpha(GOLD, 200) : HudDraw.withAlpha(RED, 230);
            HudDraw.line(m, border, x, 0, x + size, 0, 0.7f);
            HudDraw.line(m, border, x + size, 0, x + size, size + 9f, 0.7f);
            HudDraw.line(m, border, x + size, size + 9f, x, size + 9f, 0.7f);
            HudDraw.line(m, border, x, size + 9f, x, 0, 0.7f);
            TextureAtlasSprite sprite = mc.getMobEffectTextures().get(e.getEffect());
            g.blit(Math.round(x + 1f), 1, 0, 16, 16, sprite);
            String t = MobEffectUtil.formatDuration(e, 1.0f).getString();
            g.pose().pushPose();
            g.pose().translate(x + size / 2f, size + 1.5f, 0);
            g.pose().scale(0.6f, 0.6f, 1f);
            g.drawString(font, t, Math.round(-font.width(t) / 2f), 0, BONE, true);
            g.pose().popPose();
        }
        g.pose().popPose();
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}

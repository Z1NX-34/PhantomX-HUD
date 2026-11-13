package com.phantomx.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Overlay registration, vanilla / other-mod HUD hiding and the boss bar hook. */
public final class HudEvents {
    private HudEvents() {}

    private static final Logger LOG = LogManager.getLogger("PhantomXHud");

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

        private static final ResourceLocation[] SCH = {
            new ResourceLocation("souls_combat_hud", "player_gauge_overlay"),
            new ResourceLocation("souls_combat_hud", "experience_overlay"),
            new ResourceLocation("souls_combat_hud", "skill_overlay"),
            new ResourceLocation("souls_combat_hud", "equipment_hud"),
            new ResourceLocation("souls_combat_hud", "souls_bossbar"),
        };
        private static final ResourceLocation ADV_COMPASS = new ResourceLocation("advancedcompass", "compass");

        @SubscribeEvent
        public static void hideOthers(RenderGuiOverlayEvent.Pre e) {
            if (!HudConfig.ENABLED.get()) return;
            ResourceLocation id = e.getOverlay().id();
            boolean hide = id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                    || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
                    || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                    || (HudConfig.SHOW_XP.get() && id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id()))
                    || (HudConfig.SHOW_EFFECTS.get() && id.equals(VanillaGuiOverlay.POTION_ICONS.id()))
                    || (HudConfig.SHOW_COMPASS.get() && id.equals(ADV_COMPASS));
            if (!hide && HudConfig.HIDE_OTHER_HUDS.get()) {
                for (ResourceLocation r : SCH) {
                    if (r.equals(id)) {
                        hide = true;
                        break;
                    }
                }
            }
            if (!hide && matchesHideList(id)) hide = true;
            if (hide) e.setCanceled(true);
        }

        private static boolean matchesHideList(ResourceLocation id) {
            String full = id.toString();
            for (String pat : HudConfig.HIDE_OVERLAYS.get()) {
                if (pat.endsWith("*")) {
                    if (full.startsWith(pat.substring(0, pat.length() - 1))) return true;
                } else if (full.equals(pat)) {
                    return true;
                }
            }
            return false;
        }

        // held item name: lifted so it clears the XP bar. Lowest priority so we only push when nobody cancelled it.
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void itemNamePre(RenderGuiOverlayEvent.Pre e) {
            if (!HudConfig.ENABLED.get() || !e.getOverlay().id().equals(VanillaGuiOverlay.ITEM_NAME.id())) return;
            e.getGuiGraphics().pose().pushPose();
            e.getGuiGraphics().pose().translate(0f, -HudConfig.ITEM_NAME_RAISE.get(), 0f);
            itemNamePushed = true;
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void itemNamePost(RenderGuiOverlayEvent.Post e) {
            if (itemNamePushed && e.getOverlay().id().equals(VanillaGuiOverlay.ITEM_NAME.id())) {
                e.getGuiGraphics().pose().popPose();
                itemNamePushed = false;
            }
        }

        private static boolean itemNamePushed;

        // chat: moved right and up so it no longer sits on the player block and equipment slots
        @SubscribeEvent
        public static void chat(CustomizeGuiOverlayEvent.Chat e) {
            if (!HudConfig.ENABLED.get() || !HudConfig.MOVE_CHAT.get()) return;
            float scale = HudConfig.SCALE.get().floatValue();
            e.setPosX(e.getPosX() + Math.round(HudConfig.CHAT_SHIFT_X.get() * scale));
            e.setPosY(e.getPosY() - Math.round(HudConfig.CHAT_SHIFT_Y.get() * scale));
        }

        @SubscribeEvent
        public static void boss(CustomizeGuiOverlayEvent.BossEventProgress e) {
            if (!HudConfig.ENABLED.get() || !HudConfig.SHOW_BOSS.get()) return;
            e.setCanceled(true);
            try {
                Minecraft mc = Minecraft.getInstance();
                int idx = Math.max(0, (e.getY() - 12) / Math.max(1, e.getIncrement()));
                BossBars.draw(e.getGuiGraphics(), e.getBossEvent(), idx,
                        mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                        HudConfig.SCALE.get().floatValue());
            } catch (Throwable t) {
                LOG.warn("boss bar error: {}", t.toString());
            }
        }
    }

    private static void render(GuiGraphics g, float partial, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.options.hideGui || p.isSpectator()) return;
        float scale = HudConfig.SCALE.get().floatValue();

        if (HudConfig.SHOW_COMPASS.get()) CompassBar.render(g, mc.font, p, sw, scale, partial);
        PlayerBlock.render(g, p, sw, sh, scale);
        float top = PlayerBlock.crestTopScreenY(sh, scale);
        if (HudConfig.SHOW_SKILLS.get()) top = SkillRow.render(g, p, sh, scale, partial);
        if (HudConfig.SHOW_EQUIPMENT.get()) EquipmentSlots.render(g, mc.font, p, sw, sh, scale, partial);
        if (HudConfig.SHOW_EFFECTS.get()) EffectsStrip.render(g, mc.font, p, scale, top);
    }
}

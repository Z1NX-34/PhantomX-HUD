package com.phantomx.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.GuiOverlayManager;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.nio.file.Files;
import java.util.Locale;

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
            if (!hide && HudConfig.BUILTIN_HIDE.get() && builtinHide(id)) hide = true;
            if (!hide && HudConfig.SHOW_BOSS.get() && HudConfig.AUTO_HIDE_BOSS.get() && looksLikeBossOverlay(id)) hide = true;
            if (hide && !protectedOverlay(id)) e.setCanceled(true);
        }

        /** Epic Fight's HUD and Iron's Spells' mana bar / spell slots (its cast and imbue overlays stay). */
        private static boolean builtinHide(ResourceLocation id) {
            String ns = id.getNamespace(), path = id.getPath().toLowerCase(Locale.ROOT);
            if (ns.equals("epicfight")) return true;
            return ns.equals("irons_spellbooks") && (path.equals("mana_overlay") || path.equals("spell_bar"));
        }

        /** Overlays that must always stay: Iron's spell wheel (hold-to-select), cast / recast bars, screen effects, tooltips. */
        private static boolean protectedOverlay(ResourceLocation id) {
            if (!id.getNamespace().equals("irons_spellbooks")) return false;
            String path = id.getPath();
            return path.equals("spell_wheel") || path.equals("cast_bar") || path.equals("recast_bar")
                    || path.equals("screen_effects") || path.equals("screen_tooltip");
        }

        private static boolean looksLikeBossOverlay(ResourceLocation id) {
            String ns = id.getNamespace();
            if (ns.equals("minecraft") || ns.equals(PhantomXHud.MODID)) return false;
            String p = id.getPath().toLowerCase(Locale.ROOT);
            if (!p.contains("boss")) return false;
            for (String bad : new String[] {"intro", "title", "cinematic", "cutscene", "warning"}) {
                if (p.contains(bad)) return false;
            }
            return true;
        }

        // Vanilla boss bars and the vanilla hotbar are NOT cancelled (that stops mods that hook them, e.g. boss intros).
        // They are drawn 20000 px above the screen instead, so every hook still runs but nothing is visible.
        private static ResourceLocation sunk;

        private static boolean sinkWanted(RenderGuiOverlayEvent e) {
            if (!HudConfig.ENABLED.get()) return false;
            ResourceLocation id = e.getOverlay().id();
            if (id.equals(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id())) return HudConfig.SHOW_BOSS.get() && !BossBars.unavailable();
            if (id.equals(VanillaGuiOverlay.HOTBAR.id())) {
                Player p = Minecraft.getInstance().player;
                return HudConfig.SHOW_HOTBAR.get() && p != null && !p.isSpectator() && !Minecraft.getInstance().options.hideGui;
            }
            return false;
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void sinkPre(RenderGuiOverlayEvent.Pre e) {
            if (!sinkWanted(e)) return;
            e.getGuiGraphics().pose().pushPose();
            e.getGuiGraphics().pose().translate(0f, -20000f, 0f);
            sunk = e.getOverlay().id();
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public static void sinkCancelled(RenderGuiOverlayEvent.Pre e) {
            if (sunk != null && e.isCanceled() && sunk.equals(e.getOverlay().id())) {
                e.getGuiGraphics().pose().popPose();
                sunk = null;
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public static void sinkPost(RenderGuiOverlayEvent.Post e) {
            if (sunk != null && sunk.equals(e.getOverlay().id())) {
                e.getGuiGraphics().pose().popPose();
                sunk = null;
            }
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
    }

    private static void render(GuiGraphics g, float partial, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.options.hideGui || p.isSpectator()) return;
        float scale = HudConfig.SCALE.get().floatValue();
        logOverlaysOnce();

        if (HudConfig.SHOW_COMPASS.get()) CompassBar.render(g, mc.font, p, sw, scale, partial);
        PlayerBlock.render(g, p, sw, sh, scale);
        float top = PlayerBlock.crestTopScreenY(sh, scale);
        if (HudConfig.SHOW_SKILLS.get()) top = SkillRow.render(g, p, sh, scale, partial);
        if (HudConfig.SHOW_EQUIPMENT.get()) EquipmentSlots.render(g, mc.font, p, sw, sh, scale, partial);
        if (HudConfig.SHOW_HOTBAR.get()) HotbarBar.render(g, mc.font, p, sw, sh, scale, partial);
        if (HudConfig.SHOW_EFFECTS.get()) EffectsStrip.render(g, mc.font, p, sw, sh, scale);
        if (HudConfig.SHOW_BOSS.get()) BossBars.renderAll(g, sw, sh, scale);
    }

    private static boolean logged;

    /** Writes every registered overlay id to the log once, so ids for the hide list can be looked up. */
    private static void logOverlaysOnce() {
        if (logged) return;
        logged = true;
        try {
            StringBuilder sb = new StringBuilder();
            for (NamedGuiOverlay o : GuiOverlayManager.getOverlays()) sb.append(o.id()).append(", ");
            LOG.info("Registered HUD overlays: {}", sb);
            StringBuilder file = new StringBuilder("# Every HUD overlay id registered in this game (written by PhantomX HUD at startup).\n"
                    + "# Add ids you want hidden to hide_overlays in phantomx_hud-client.toml ('modid:*' hides a whole mod).\n");
            for (NamedGuiOverlay o : GuiOverlayManager.getOverlays()) file.append(o.id()).append('\n');
            Files.writeString(FMLPaths.CONFIGDIR.get().resolve("phantomx_hud-overlays.txt"), file.toString());
        } catch (Throwable t) {
            LOG.warn("Could not list overlays: {}", t.toString());
        }
    }
}

package com.phantomx.hud;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Reads the player's Epic Fight skills by reflection and lets Epic Fight draw each icon itself (cooldowns included). */
final class EpicFightSkills {
    private EpicFightSkills() {}

    private static final Logger LOG = LogManager.getLogger("PhantomXHud/EpicFight");
    private static boolean failed, init;
    private static Method engineGet, enginePatch, patchGetSkill, containerEmpty, containerGetSkill, skillShouldDraw, skillDraw;
    private static Method managerValues;
    private static Object slotManager;
    private static Field renderEngineF, battleUiF;
    private static Class<?> weaponInnateCls;
    private static Method slideM;
    private static Method itemCap;
    private static Class<?> weaponCapCls, rangedCapCls;
    private static boolean capFailed;

    /** SkillContainer objects worth drawing, in Epic Fight's slot order. Empty when Epic Fight is absent. */
    static List<Object> visible() {
        List<Object> out = new ArrayList<>();
        if (failed || !ModList.get().isLoaded("epicfight")) return out;
        try {
            if (!init) {
                Class<?> ce = Class.forName("yesman.epicfight.client.ClientEngine");
                engineGet = ce.getMethod("getInstance");
                enginePatch = ce.getMethod("getPlayerPatch");
                renderEngineF = ce.getField("renderEngine");
                Class<?> re = Class.forName("yesman.epicfight.client.events.engine.RenderEngine");
                battleUiF = re.getField("battleModeUI");
                Class<?> slot = Class.forName("yesman.epicfight.skill.SkillSlot");
                Class<?> container = Class.forName("yesman.epicfight.skill.SkillContainer");
                Class<?> skill = Class.forName("yesman.epicfight.skill.Skill");
                Class<?> bm = Class.forName("yesman.epicfight.client.gui.BattleModeGui");
                Class<?> lpp = Class.forName("yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch");
                patchGetSkill = lpp.getMethod("getSkill", slot);
                containerEmpty = container.getMethod("isEmpty");
                containerGetSkill = container.getMethod("getSkill");
                skillShouldDraw = skill.getMethod("shouldDraw", container);
                skillDraw = skill.getMethod("drawOnGui", bm, container, GuiGraphics.class, float.class, float.class, float.class);
                slotManager = slot.getField("ENUM_MANAGER").get(null);
                managerValues = slotManager.getClass().getMethod("universalValues");
                weaponInnateCls = Class.forName("yesman.epicfight.skill.weaponinnate.WeaponInnateSkill");
                slideM = bm.getMethod("getSlidingProgression");
                init = true;
            }
            Object patch = enginePatch.invoke(engineGet.invoke(null));
            if (patch == null) return out;
            Collection<?> slots = (Collection<?>) managerValues.invoke(slotManager);
            for (Object s : slots) {
                Object c = patchGetSkill.invoke(patch, s);
                if (c == null || (Boolean) containerEmpty.invoke(c)) continue;
                Object sk = containerGetSkill.invoke(c);
                if (sk == null || !(Boolean) skillShouldDraw.invoke(sk, c)) continue;
                if (weaponInnateCls.isInstance(sk)) out.add(0, c); else out.add(c);
                if (out.size() >= 7) break;
            }
        } catch (Throwable t) {
            failed = true;
            LOG.warn("Epic Fight skills unavailable: {}", t.toString());
            out.clear();
        }
        return out;
    }

    /** Draws one skill icon centred on (cx, cy), fitted into a square of the given pixel size. */
    static void draw(GuiGraphics g, Object container, float cx, float cy, float size, float partial) {
        if (failed) return;
        try {
            Object skill = containerGetSkill.invoke(container);
            Object battleUi = battleUiF.get(renderEngineF.get(engineGet.invoke(null)));
            float native_ = weaponInnateCls.isInstance(skill) ? 32f : 24f;
            float s = size / native_;
            g.pose().pushPose();
            g.pose().translate(cx - size / 2f, cy - size / 2f, 0f);
            g.pose().scale(s, s, 1f);
            try {
                // Epic Fight slides its icons down when you are not in combat mode; cancel that so the icon stays put
                g.pose().translate(0f, -((Number) slideM.invoke(battleUi)).floatValue(), 0f);
                skillDraw.invoke(skill, battleUi, container, g, 0f, 0f, partial);
            } finally {
                g.pose().popPose();
            }
        } catch (Throwable t) {
            failed = true;
            LOG.warn("Epic Fight skill draw failed: {}", t.toString());
        }
    }

    /** True when this container holds a weapon-innate (special move) skill. */
    static boolean isInnate(Object container) {
        if (failed || !init) return false;
        try {
            return weaponInnateCls.isInstance(containerGetSkill.invoke(container));
        } catch (Throwable t) {
            return false;
        }
    }

    /** True when Epic Fight treats the stack as a combat weapon (melee or ranged capability). */
    static boolean isCombatItem(net.minecraft.world.item.ItemStack stack) {
        if (capFailed || !ModList.get().isLoaded("epicfight")) return false;
        try {
            if (itemCap == null) {
                Class<?> caps = Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities");
                itemCap = caps.getMethod("getItemStackCapability", net.minecraft.world.item.ItemStack.class);
                weaponCapCls = Class.forName("yesman.epicfight.world.capabilities.item.WeaponCapability");
                rangedCapCls = Class.forName("yesman.epicfight.world.capabilities.item.RangedWeaponCapability");
            }
            Object cap = itemCap.invoke(null, stack);
            return cap != null && (weaponCapCls.isInstance(cap) || rangedCapCls.isInstance(cap));
        } catch (Throwable t) {
            capFailed = true;
            LOG.warn("Epic Fight item capability unavailable: {}", t.toString());
            return false;
        }
    }
}

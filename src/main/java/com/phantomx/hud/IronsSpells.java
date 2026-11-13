package com.phantomx.hud;

import java.lang.reflect.Method;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Selected spell of Iron's Spells 'n Spellbooks, by reflection. */
final class IronsSpells {
    private IronsSpells() {}

    private static final Logger LOG = LogManager.getLogger("PhantomXHud/Irons");
    private static boolean failed, init;
    private static Class<?> magicData;
    private static Method getManager, getCount, getSelected, spellOf, iconOf, cooldownOf;

    private static Object selectedSpell() throws Exception {
        Object mgr = getManager.invoke(null);
        if (mgr == null || (Integer) getCount.invoke(mgr) <= 0) return null;
        Object data = getSelected.invoke(mgr);
        return data == null ? null : spellOf.invoke(data);
    }

    private static boolean ready() {
        if (failed || !ModList.get().isLoaded("irons_spellbooks")) return false;
        if (init) return true;
        try {
            magicData = Class.forName("io.redspace.ironsspellbooks.player.ClientMagicData");
            getManager = magicData.getMethod("getSpellSelectionManager");
            Class<?> mgr = Class.forName("io.redspace.ironsspellbooks.api.magic.SpellSelectionManager");
            getCount = mgr.getMethod("getSpellCount");
            getSelected = mgr.getMethod("getSelectedSpellData");
            Class<?> data = Class.forName("io.redspace.ironsspellbooks.api.spells.SpellData");
            spellOf = data.getMethod("getSpell");
            Class<?> abs = Class.forName("io.redspace.ironsspellbooks.api.spells.AbstractSpell");
            iconOf = abs.getMethod("getSpellIconResource");
            cooldownOf = magicData.getMethod("getCooldownPercent", abs);
            init = true;
            return true;
        } catch (Throwable t) {
            failed = true;
            LOG.warn("Iron's Spells unavailable: {}", t.toString());
            return false;
        }
    }

    /** True when Iron's Spells is installed and readable. */
    static boolean loaded() {
        return ready();
    }

    /** Icon of the selected spell or null. */
    static ResourceLocation icon() {
        if (!ready()) return null;
        try {
            Object sp = selectedSpell();
            return sp == null ? null : (ResourceLocation) iconOf.invoke(sp);
        } catch (Throwable t) {
            failed = true;
            LOG.warn("Iron's Spells icon failed: {}", t.toString());
            return null;
        }
    }

    /** Cooldown fraction 0..1 of the selected spell. */
    static float cooldown() {
        if (!ready()) return 0f;
        try {
            Object sp = selectedSpell();
            return sp == null ? 0f : (Float) cooldownOf.invoke(null, sp);
        } catch (Throwable t) {
            return 0f;
        }
    }
}

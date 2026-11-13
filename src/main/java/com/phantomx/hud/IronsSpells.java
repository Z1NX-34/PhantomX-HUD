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
    private static Method getManager, getCount, getSelected, getIndex, getDataAt, spellOf, iconOf, cooldownOf;

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
            getIndex = mgr.getMethod("getSelectionIndex");
            getDataAt = mgr.getMethod("getSpellData", int.class);
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

    /** One spell of the selection list. */
    static final class Entry {
        final ResourceLocation icon;
        final boolean selected;
        final float cooldown;

        Entry(ResourceLocation icon, boolean selected, float cooldown) {
            this.icon = icon;
            this.selected = selected;
            this.cooldown = cooldown;
        }
    }

    /** Total number of selectable spells. */
    static int count() {
        if (!ready()) return 0;
        try {
            Object mgr = getManager.invoke(null);
            return mgr == null ? 0 : (Integer) getCount.invoke(mgr);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Up to 'size' spells around the selected one (no wrap-around, window clamped to the list). */
    static java.util.List<Entry> window(int size) {
        java.util.List<Entry> out = new java.util.ArrayList<>();
        if (!ready()) return out;
        try {
            Object mgr = getManager.invoke(null);
            if (mgr == null) return out;
            int n = (Integer) getCount.invoke(mgr);
            if (n <= 0) return out;
            int sel = (Integer) getIndex.invoke(mgr);
            int from = Math.max(0, Math.min(sel - size / 2, n - size));
            int to = Math.min(n, from + size);
            for (int i = from; i < to; i++) {
                Object data = getDataAt.invoke(mgr, i);
                Object sp = data == null ? null : spellOf.invoke(data);
                if (sp == null) continue;
                float cd = (Float) cooldownOf.invoke(null, sp);
                out.add(new Entry((ResourceLocation) iconOf.invoke(sp), i == sel, cd));
            }
        } catch (Throwable t) {
            failed = true;
            LOG.warn("Iron's Spells list failed: {}", t.toString());
        }
        return out;
    }
}

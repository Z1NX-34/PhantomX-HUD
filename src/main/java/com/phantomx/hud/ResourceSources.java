package com.phantomx.hud;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Stamina (Epic Fight) and mana (Iron's Spells 'n Spellbooks), read by reflection so neither mod is required. */
final class ResourceSources {
    private ResourceSources() {}

    private static final Logger LOG = LogManager.getLogger("PhantomXHud");

    private static boolean efFailed, isFailed;
    private static Method efEngine, efPatch, efStamina, efMax;
    private static Method isMana;
    private static Object isMaxMana;

    /** {current, max} or null when stamina is not available. */
    static float[] stamina() {
        if (efFailed || !ModList.get().isLoaded("epicfight")) return null;
        try {
            if (efEngine == null) {
                Class<?> ce = Class.forName("yesman.epicfight.client.ClientEngine");
                efEngine = ce.getMethod("getInstance");
                efPatch = ce.getMethod("getPlayerPatch");
                Class<?> pp = Class.forName("yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch");
                efStamina = pp.getMethod("getStamina");
                efMax = pp.getMethod("getMaxStamina");
            }
            Object patch = efPatch.invoke(efEngine.invoke(null));
            if (patch == null) return null;
            float max = (Float) efMax.invoke(patch);
            if (max <= 0f) return null;
            return new float[] {Math.max(0f, (Float) efStamina.invoke(patch)), max};
        } catch (Throwable t) {
            efFailed = true;
            LOG.warn("Epic Fight stamina unavailable: {}", t.toString());
            return null;
        }
    }

    /** {current, max} or null when mana is not available. */
    @SuppressWarnings("unchecked")
    static float[] mana() {
        if (isFailed || !ModList.get().isLoaded("irons_spellbooks")) return null;
        try {
            Player p = Minecraft.getInstance().player;
            if (p == null) return null;
            if (isMana == null) {
                Class<?> cm = Class.forName("io.redspace.ironsspellbooks.player.ClientMagicData");
                isMana = cm.getMethod("getPlayerMana");
                Class<?> ar = Class.forName("io.redspace.ironsspellbooks.api.registry.AttributeRegistry");
                isMaxMana = ar.getField("MAX_MANA").get(null);
            }
            Attribute attr = ((RegistryObject<Attribute>) isMaxMana).get();
            double max = p.getAttributeValue(attr);
            if (max <= 0) return null;
            return new float[] {Math.max(0f, ((Number) isMana.invoke(null)).floatValue()), (float) max};
        } catch (Throwable t) {
            isFailed = true;
            LOG.warn("Iron's Spells mana unavailable: {}", t.toString());
            return null;
        }
    }
}

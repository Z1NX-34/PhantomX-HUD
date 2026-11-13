package com.phantomx.hud;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Finds the entity behind a boss bar and reads its posture from Epic Fight Posture Integration (by reflection), so the
 * boss bar can show real HP numbers and a posture gauge.
 */
final class PostureLink {
    private PostureLink() {}

    private static final Logger LOG = LogManager.getLogger("PhantomXHud/Posture");

    /** What we know about a boss bar's entity. */
    static final class Info {
        LivingEntity entity;
        float posture = -1f;          // 0..1, -1 when the entity has no posture
        boolean broken;
    }

    private static boolean failed, init;
    private static Method storeGet;
    private static Field curF, maxF, brokenF;

    private static final java.util.Map<UUID, Integer> CACHE = new java.util.HashMap<>();

    static Info lookup(Minecraft mc, LerpingBossEvent ev) {
        Info info = new Info();
        if (mc.level == null) return info;
        LivingEntity e = null;
        Integer cached = CACHE.get(ev.getId());
        if (cached != null) {
            Entity c = mc.level.getEntity(cached);
            if (c instanceof LivingEntity le && le.isAlive()) e = le;
        }
        if (e == null) {
            e = find(mc, ev);
            if (e != null) CACHE.put(ev.getId(), e.getId());
            else CACHE.remove(ev.getId());
            if (CACHE.size() > 64) CACHE.clear();
        }
        info.entity = e;
        if (e != null) readPosture(e, info);
        return info;
    }

    private static LivingEntity find(Minecraft mc, LerpingBossEvent ev) {
        String boss = norm(ev.getName().getString());
        float pct = ev.getProgress();
        LivingEntity byName = null, byHealth = null;
        double bestName = Double.MAX_VALUE, bestHealth = 0;
        Player me = mc.player;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!(en instanceof LivingEntity le) || le == me || !le.isAlive()) continue;
            double d = me == null ? 0 : le.distanceToSqr(me);
            if (d > 160.0 * 160.0) continue;
            String n = norm(le.getName().getString());
            if (n.length() >= 4 && (boss.contains(n) || (boss.length() >= 4 && n.contains(boss)) || sharedWord(boss, le.getName().getString()))) {
                if (d < bestName) {
                    bestName = d;
                    byName = le;
                }
            } else if (le.getMaxHealth() >= 40f && Math.abs(le.getHealth() / le.getMaxHealth() - pct) < 0.012f && le.getMaxHealth() > bestHealth) {
                bestHealth = le.getMaxHealth();
                byHealth = le;
            }
        }
        return byName != null ? byName : byHealth;
    }

    private static boolean sharedWord(String bossNorm, String entityRaw) {
        for (String w : entityRaw.split("[^\\p{L}\\p{N}]+")) {
            String t = w.toLowerCase(java.util.Locale.ROOT);
            if (t.length() >= 5 && bossNorm.contains(t)) return true;
        }
        return false;
    }

    private static String norm(String s) {
        return s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static void readPosture(Entity e, Info info) {
        if (failed || !ModList.get().isLoaded("epicfight_posture_integration")) return;
        try {
            if (!init) {
                Class<?> store = Class.forName("dev.nire9734.epicfightposture.client.ClientPostureStore");
                storeGet = store.getMethod("get", int.class, UUID.class);
                Class<?> entry = Class.forName("dev.nire9734.epicfightposture.client.ClientPostureStore$Entry");
                curF = entry.getField("current");
                maxF = entry.getField("max");
                brokenF = entry.getField("broken");
                init = true;
            }
            Object entry = storeGet.invoke(null, e.getId(), e.getUUID());
            if (entry == null) return;
            float max = maxF.getFloat(entry);
            if (max <= 0f) return;
            info.posture = Math.max(0f, Math.min(1f, curF.getFloat(entry) / max));
            info.broken = brokenF.getBoolean(entry);
        } catch (Throwable t) {
            failed = true;
            LOG.warn("Posture data unavailable: {}", t.toString());
        }
    }
}

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
        boolean byName;               // matched through the boss name (reliable), not just a health coincidence
        float posture = -1f;          // 0..1, -1 when the entity has no posture
        boolean broken;
    }

    private static final class Found {
        LivingEntity entity;
        boolean byName;
    }

    private static final class Cached {
        int id = -1;
        boolean byName;
        long next;
    }

    private static boolean failed, init;
    private static Method storeGet;
    private static Field curF, maxF, brokenF;

    private static final java.util.Map<UUID, Cached> CACHE = new java.util.HashMap<>();

    static Info lookup(Minecraft mc, LerpingBossEvent ev) {
        Info info = new Info();
        if (mc.level == null) return info;
        long now = System.nanoTime();
        Cached c = CACHE.get(ev.getId());
        LivingEntity e = null;
        boolean byName = false;
        if (c != null && c.id >= 0) {
            Entity ce = mc.level.getEntity(c.id);
            if (ce instanceof LivingEntity le && le.isAlive()) {
                e = le;
                byName = c.byName;
            }
        }
        boolean refresh = c == null || now >= c.next || (e == null && c.id >= 0);
        if (refresh) {
            Found f = find(mc, ev);
            e = f == null ? null : f.entity;
            byName = f != null && f.byName;
            Cached nc = new Cached();
            nc.id = e == null ? -1 : e.getId();
            nc.byName = byName;
            nc.next = now + 400_000_000L;
            CACHE.put(ev.getId(), nc);
            if (CACHE.size() > 64) CACHE.clear();
        }
        info.entity = e;
        info.byName = byName;
        if (e != null) readPosture(e, info);
        return info;
    }

    /**
     * Candidates whose name matches the boss name win; among several (two bosses with one name) the one whose health
     * fraction is closest to the bar is taken. Without a name match, a big entity with exactly the bar's health fraction.
     */
    private static Found find(Minecraft mc, LerpingBossEvent ev) {
        String boss = norm(ev.getName().getString());
        float pct = ev.getProgress();
        LivingEntity byName = null, byHealth = null;
        float bestDiff = Float.MAX_VALUE;
        double bestDist = Double.MAX_VALUE, bestHealth = 0;
        Player me = mc.player;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!(en instanceof LivingEntity le) || en instanceof Player || !le.isAlive()) continue;
            double d = me == null ? 0 : le.distanceToSqr(me);
            if (d > 160.0 * 160.0) continue;
            String n = norm(le.getName().getString());
            float frac = le.getMaxHealth() > 0f ? le.getHealth() / le.getMaxHealth() : 0f;
            if (n.length() >= 4 && (boss.contains(n) || (boss.length() >= 4 && n.contains(boss)) || sharedWord(boss, le.getName().getString()))) {
                float diff = Math.abs(frac - pct);
                if (diff < bestDiff - 0.005f || (Math.abs(diff - bestDiff) <= 0.005f && d < bestDist)) {
                    bestDiff = diff;
                    bestDist = d;
                    byName = le;
                }
            } else if (le.getMaxHealth() >= 40f && Math.abs(frac - pct) < 0.012f && le.getMaxHealth() > bestHealth) {
                bestHealth = le.getMaxHealth();
                byHealth = le;
            }
        }
        Found f = new Found();
        if (byName != null) {
            f.entity = byName;
            f.byName = true;
            return f;
        }
        if (byHealth != null) {
            f.entity = byHealth;
            return f;
        }
        return null;
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

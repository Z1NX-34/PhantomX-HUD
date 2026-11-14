package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Four diamond slots next to the hotbar: weapon (right), offhand (left), consumable (bottom), spell (top). Each has a
 * small gauge on its outer vertex. The weapon slot only reacts to combat items and keeps showing the last weapon held.
 * With Iron's Spells the whole selection list is shown as a strip of small diamonds above the spell slot.
 */
final class EquipmentSlots {
    private EquipmentSlots() {}

    private static final float HS = 13f, STEP = HS + 2.3f;       // clear gap between neighbouring diamonds
    private static ItemStack lastWeapon = ItemStack.EMPTY;

    static void render(GuiGraphics g, Font font, Player p, int sw, int sh, float scale, float partial) {
        float cx = sw / 2f + 91f * scale + HudConfig.EQUIP_X.get() * scale;
        float cy = sh - HudConfig.EQUIP_Y.get() * scale;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1f);

        // weapon
        ItemStack main = p.getMainHandItem();
        boolean holding = isWeapon(main);
        if (holding) lastWeapon = main.copy();
        ItemStack w = holding ? main : lastWeapon;
        slotFrame(g, +STEP, 0, HS, !w.isEmpty(), holding, 0);
        if (!w.isEmpty()) {
            drawItem(g, font, w, +STEP, 0, holding, false);
            durability(g, w, +STEP + HS + 3.6f, 0, holding);
        }

        // offhand
        ItemStack off = p.getOffhandItem();
        slotFrame(g, -STEP, 0, HS, !off.isEmpty(), false, 1);
        if (!off.isEmpty()) {
            drawItem(g, font, off, -STEP, 0, true, true);
            durability(g, off, -STEP - HS - 3.6f, 0, true);
            float cd = p.getCooldowns().getCooldownPercent(off.getItem(), partial);
            if (cd > 0f) cooldownVeil(g, -STEP, 0, cd);
        }

        // consumable
        ItemStack cons = consumable(p);
        slotFrame(g, 0, +STEP, HS, !cons.isEmpty(), false, 2);
        if (!cons.isEmpty()) {
            drawItem(g, font, cons, 0, +STEP, true, true);
            float cd = p.getCooldowns().getCooldownPercent(cons.getItem(), partial);
            if (cd > 0f) cooldownVeil(g, 0, +STEP, cd);
            float frac = Math.min(1f, cons.getCount() / (float) Math.max(1, Math.min(16, cons.getMaxStackSize())));
            gauge(g, -6f, +STEP + HS + 3.6f, 12f, 1.7f, frac, argb(222, 150, 56, 255));
        }

        // spells (only when Iron's Spells is installed)
        if (IronsSpells.loaded()) spells(g, font);
        g.pose().popPose();
    }

    // ---------------------------------------------------------------------------------------------- spells

    private static void spells(GuiGraphics g, Font font) {
        List<IronsSpells.Entry> list = IronsSpells.window(5);
        IronsSpells.Entry sel = null;
        for (IronsSpells.Entry e : list) if (e.selected) sel = e;

        slotFrame(g, 0, -STEP, HS, sel != null, sel != null, 3);
        if (sel != null) {
            diamondIcon(g, sel.icon, 0, -STEP, HS - 1.8f, 16);
            if (sel.cooldown > 0f) {
                Matrix4f m = g.pose().last().pose();
                diamondFill(m, withAlpha(INK, 150), 0, -STEP, (HS - 2.5f) * sel.cooldown, (HS - 2.5f) * sel.cooldown);
            }
            gauge(g, -6f, -STEP - HS - 3.6f, 12f, 1.7f, 1f - sel.cooldown, sel.cooldown > 0f ? DGOLD : GOLD);
        }

        // the spell list (at most five) as small diamonds running diagonally up and to the right from the spell slot,
        // so it never reaches over towards the boss bar
        if (list.size() > 1) {
            float hs = 5.6f, d = 0.7071f, step = hs * 1.41421f + 2.2f;
            float t0 = HS * d + 3.2f + hs * d;
            for (int i = 0; i < list.size() && i < 5; i++) {
                IronsSpells.Entry e = list.get(i);
                float t = t0 + i * step;
                float x = t * d, y = -STEP - t * d;
                Matrix4f m = g.pose().last().pose();
                diamondFill(m, withAlpha(INK, 232), x, y, hs, hs);
                diamondIcon(g, e.icon, x, y, hs - 1f, 8);
                if (e.cooldown > 0f) diamondFill(g.pose().last().pose(), withAlpha(INK, 160), x, y, (hs - 1f) * e.cooldown, (hs - 1f) * e.cooldown);
                m = g.pose().last().pose();
                diamondLine(m, e.selected ? GOLD : withAlpha(DGOLD, 200), x, y, hs, hs, e.selected ? 1.1f : 0.7f);
                if (!e.selected) diamondFill(m, withAlpha(INK, 110), x, y, hs - 1f, hs - 1f);
            }
        }
    }

    /**
     * Draws a square icon cropped to a diamond of half-diagonal 'hm' around (cx, cy): the four corners outside it are
     * masked out through the depth buffer.
     */
    private static void diamondIcon(GuiGraphics g, ResourceLocation icon, float cx, float cy, float hm, int px) {
        Matrix4f m = g.pose().last().pose();
        float ext = px / 2f + 3f, d = hm - px / 2f;      // corner triangles lie on the lines |x|+|y| = hm
        g.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.colorMask(false, false, false, false);
        try {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    polyZ(m, WHITE, 300f,
                            cx + sx * hm, cy, cx + sx * ext, cy, cx + sx * ext, cy + sy * ext, cx, cy + sy * ext, cx, cy + sy * hm);
                }
            }
        } finally {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        }
        try {
            g.blit(icon, Math.round(cx - px / 2f), Math.round(cy - px / 2f), 0, 0, px, px, px, px);
        } finally {
            g.flush();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        }
    }

    // ---------------------------------------------------------------------------------------------- logic

    private static boolean isWeapon(ItemStack s) {
        if (s.isEmpty()) return false;
        Item it = s.getItem();
        if (it instanceof ProjectileWeaponItem || it instanceof TridentItem || it instanceof ShieldItem) return true;
        if (EpicFightSkills.isCombatItem(s)) return true;
        if (it instanceof DiggerItem && !(it instanceof AxeItem)) return false;
        return !s.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).isEmpty();
    }

    private static ItemStack consumable(Player p) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().items.get(i);
            if (isConsumable(s)) return s;
        }
        for (ItemStack s : p.getInventory().items) if (isConsumable(s)) return s;
        return ItemStack.EMPTY;
    }

    private static boolean isConsumable(ItemStack s) {
        return !s.isEmpty() && (s.isEdible() || s.getItem() instanceof PotionItem);
    }

    // ---------------------------------------------------------------------------------------------- drawing

    /** glyph: 0 sword, 1 shield, 2 flask, 3 star (shown dim while the slot is empty). */
    private static void slotFrame(GuiGraphics g, float x, float y, float hs, boolean lit, boolean active, int glyph) {
        Matrix4f m = g.pose().last().pose();
        diamondFill(m, withAlpha(INK, 238), x, y, hs, hs);
        float hi = hs - 2.2f;
        int centre = withAlpha(argb(78, 66, 150, 255), active ? 235 : 160);
        int rim = withAlpha(argb(20, 18, 46, 255), 235);
        polyColors(m, 0f, new float[] {x, y, x, y - hi, x + hi, y, x, y + hi, x - hi, y, x, y - hi},
                new int[] {centre, rim, rim, rim, rim, rim});
        diamondLine(m, lit ? GOLD : DGOLD, x, y, hs, hs, active ? 1.5f : 1.1f);
        diamondLine(m, withAlpha(PURP, 200), x, y, hs - 2.6f, hs - 2.6f, 0.55f);
        float[][] tips = {{x, y - hs}, {x + hs, y}, {x, y + hs}, {x - hs, y}};
        for (float[] t : tips) diamondFill(m, lit ? GOLD : DGOLD, t[0], t[1], 1.5f, 1.5f);
        if (!lit) glyph(m, x, y, glyph);
    }

    private static void glyph(Matrix4f m, float x, float y, int glyph) {
        int c = withAlpha(GOLD, 95);
        switch (glyph) {
            case 0 -> {
                line(m, c, x - 4f, y + 4f, x + 4f, y - 4f, 1.1f);
                line(m, c, x - 3.2f, y - 0.8f, x + 0.8f, y + 3.2f, 1.1f);
            }
            case 1 -> poly(m, c, x - 3.6f, y - 3.8f, x + 3.6f, y - 3.8f, x + 3.6f, y + 0.4f, x, y + 4.4f, x - 3.6f, y + 0.4f);
            case 2 -> {
                poly(m, c, x - 1.1f, y - 4f, x + 1.1f, y - 4f, x + 1.1f, y - 1.4f, x - 1.1f, y - 1.4f);
                poly(m, c, x - 1.4f, y - 1.6f, x + 1.4f, y - 1.6f, x + 3.4f, y + 3.6f, x - 3.4f, y + 3.6f);
            }
            default -> poly(m, c, x, y - 4.6f, x + 1.2f, y - 1.2f, x + 4.6f, y, x + 1.2f, y + 1.2f, x, y + 4.6f, x - 1.2f, y + 1.2f, x - 4.6f, y, x - 1.2f, y - 1.2f);
        }
    }

    private static void drawItem(GuiGraphics g, Font font, ItemStack s, float x, float y, boolean bright, boolean count) {
        int ix = Math.round(x - 8f), iy = Math.round(y - 8f);
        g.renderItem(s, ix, iy);
        if (count) g.renderItemDecorations(font, s, ix, iy);
        if (!bright) {
            Matrix4f m = g.pose().last().pose();
            diamondFill(m, withAlpha(INK, 150), x, y, HS - 2.5f, HS - 2.5f);
        }
    }

    private static void cooldownVeil(GuiGraphics g, float x, float y, float cd) {
        Matrix4f m = g.pose().last().pose();
        float h = 12f * cd;
        rect(m, withAlpha(INK, 175), x - 6f, y + 6f - h, x + 6f, y + 6f);
    }

    private static void durability(GuiGraphics g, ItemStack s, float x, float y, boolean bright) {
        if (!s.isDamageableItem()) return;
        float frac = 1f - s.getDamageValue() / (float) Math.max(1, s.getMaxDamage());
        int col = frac > 0.5f ? argb(150, 200, 96, 255) : frac > 0.2f ? argb(230, 190, 70, 255) : RED;
        if (!bright) col = withAlpha(col, 120);
        gaugeV(g, x - 0.85f, y - 6f, 1.7f, 12f, frac, col);
    }

    private static void gauge(GuiGraphics g, float x, float y, float w, float h, float frac, int col) {
        Matrix4f m = g.pose().last().pose();
        rect(m, withAlpha(GOLD, 170), x - 0.6f, y - h / 2f - 0.6f, x + w + 0.6f, y + h / 2f + 0.6f);
        rect(m, withAlpha(INK, 245), x, y - h / 2f, x + w, y + h / 2f);
        if (frac > 0.01f) rect(m, col, x, y - h / 2f, x + w * Math.min(1f, frac), y + h / 2f);
    }

    private static void gaugeV(GuiGraphics g, float x, float y, float w, float h, float frac, int col) {
        Matrix4f m = g.pose().last().pose();
        rect(m, withAlpha(GOLD, 170), x - 0.6f, y - 0.6f, x + w + 0.6f, y + h + 0.6f);
        rect(m, withAlpha(INK, 245), x, y, x + w, y + h);
        if (frac > 0.01f) rect(m, col, x, y + h * (1f - Math.min(1f, frac)), x + w, y + h);
    }
}

package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

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

/**
 * Four diamond slots next to the hotbar: weapon (right), offhand (left), consumable (bottom), spell (top). Each has a
 * small gauge on its outer vertex. The weapon slot only reacts to combat items and keeps showing the last weapon held.
 */
final class EquipmentSlots {
    private EquipmentSlots() {}

    private static final float HS = 14f, STEP = HS + 0.72f;      // one pixel between neighbouring diamonds
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
        slotFrame(g, +STEP, 0, !w.isEmpty(), holding);
        if (!w.isEmpty()) {
            drawItem(g, font, w, +STEP, 0, holding, false);
            durability(g, w, +STEP + HS + 3.5f, 0, true, holding);
        }

        // offhand
        ItemStack off = p.getOffhandItem();
        slotFrame(g, -STEP, 0, !off.isEmpty(), false);
        if (!off.isEmpty()) {
            drawItem(g, font, off, -STEP, 0, true, true);
            durability(g, off, -STEP - HS - 3.5f, 0, true, true);
            float cd = p.getCooldowns().getCooldownPercent(off.getItem(), partial);
            if (cd > 0f) cooldownVeil(g, -STEP, 0, cd);
        }

        // consumable
        ItemStack cons = consumable(p);
        slotFrame(g, 0, +STEP, !cons.isEmpty(), false);
        if (!cons.isEmpty()) {
            drawItem(g, font, cons, 0, +STEP, true, true);
            float cd = p.getCooldowns().getCooldownPercent(cons.getItem(), partial);
            if (cd > 0f) cooldownVeil(g, 0, +STEP, cd);
            float frac = Math.min(1f, cons.getCount() / (float) Math.max(1, Math.min(16, cons.getMaxStackSize())));
            gauge(g, -6f, +STEP + HS + 3.5f, 12f, 1.6f, frac, argb(222, 150, 56, 255));
        }

        // spell (only when Iron's Spells is installed)
        if (IronsSpells.loaded()) {
            ResourceLocation icon = IronsSpells.icon();
            slotFrame(g, 0, -STEP, icon != null, false);
            if (icon != null) {
                g.blit(icon, Math.round(-8f), Math.round(-STEP - 8f), 0, 0, 16, 16, 16, 16);
                float cd = IronsSpells.cooldown();
                if (cd > 0f) cooldownVeil(g, 0, -STEP, cd);
                gauge(g, -6f, -STEP - HS - 3.5f, 12f, 1.6f, 1f - cd, cd > 0f ? DGOLD : GOLD);
            }
        }
        g.pose().popPose();
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

    private static void slotFrame(GuiGraphics g, float x, float y, boolean lit, boolean active) {
        Matrix4f m = g.pose().last().pose();
        diamondFill(m, withAlpha(INK, 232), x, y, HS, HS);
        diamondFill(m, withAlpha(argb(44, 38, 92, 255), active ? 190 : 120), x, y, HS - 2.5f, HS - 2.5f);
        diamondLine(m, lit ? GOLD : DGOLD, x, y, HS, HS, active ? 1.6f : 1.1f);
        diamondLine(m, withAlpha(PURP, 190), x, y, HS - 3f, HS - 3f, 0.6f);
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

    private static void durability(GuiGraphics g, ItemStack s, float x, float y, boolean vertical, boolean bright) {
        if (!s.isDamageableItem()) return;
        float frac = 1f - s.getDamageValue() / (float) Math.max(1, s.getMaxDamage());
        int col = frac > 0.5f ? argb(150, 200, 96, 255) : frac > 0.2f ? argb(230, 190, 70, 255) : RED;
        if (!bright) col = withAlpha(col, 120);
        gaugeV(g, x - 0.8f, y - 6f, 1.6f, 12f, frac, col);
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

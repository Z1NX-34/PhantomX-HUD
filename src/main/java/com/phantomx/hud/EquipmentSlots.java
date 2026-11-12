package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import org.joml.Matrix4f;

/** Bottom-right: four diamond slots (weapon right, offhand left, consumable bottom, spell top). */
final class EquipmentSlots {
    private EquipmentSlots() {}

    private static final float HS = 14f, STEP = HS + 1.6f;

    static void render(GuiGraphics g, Font font, Player p, int sw, int sh, float scale, float partial) {
        float cx = sw - HudConfig.EQUIP_X.get(), cy = sh - HudConfig.EQUIP_Y.get();
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1f);

        ItemStack main = p.getMainHandItem(), off = p.getOffhandItem(), cons = consumable(p);
        item(g, font, p, +STEP, 0, main, true, partial);
        item(g, font, p, -STEP, 0, off, false, partial);
        item(g, font, p, 0, +STEP, cons, false, partial);
        spell(g, 0, -STEP);
        g.pose().popPose();
    }

    private static ItemStack consumable(Player p) {
        for (ItemStack s : p.getInventory().items) if (!s.isEmpty() && (s.isEdible() || s.getItem() instanceof PotionItem)) return s;
        return ItemStack.EMPTY;
    }

    private static void frame(GuiGraphics g, float x, float y, boolean lit, boolean primary) {
        Matrix4f m = g.pose().last().pose();
        diamondFill(m, withAlpha(INK, 225), x, y, HS, HS);
        diamondFill(m, withAlpha(primary ? argb(60, 46, 100, 255) : argb(40, 36, 78, 255), 150), x, y, HS - 2.5f, HS - 2.5f);
        diamondLine(m, lit ? GOLD : DGOLD, x, y, HS, HS, primary ? 1.5f : 1.1f);
        diamondLine(m, withAlpha(PURP, 190), x, y, HS - 3f, HS - 3f, 0.6f);
    }

    private static void item(GuiGraphics g, Font font, Player p, float x, float y, ItemStack s, boolean primary, float partial) {
        frame(g, x, y, !s.isEmpty(), primary);
        if (s.isEmpty()) return;
        g.renderItem(s, Math.round(x - 8f), Math.round(y - 8f));
        g.renderItemDecorations(font, s, Math.round(x - 8f), Math.round(y - 8f));
        float cd = p.getCooldowns().getCooldownPercent(s.getItem(), partial);
        if (cd > 0f) {
            Matrix4f m = g.pose().last().pose();
            float h = (HS - 3f) * 2f * cd;
            rect(m, withAlpha(INK, 170), x - 6f, y + 6f - h, x + 6f, y + 6f);
        }
    }

    private static void spell(GuiGraphics g, float x, float y) {
        ResourceLocation icon = IronsSpells.icon();
        frame(g, x, y, icon != null, false);
        if (icon == null) return;
        g.blit(icon, Math.round(x - 8f), Math.round(y - 8f), 0, 0, 16, 16, 16, 16);
        float cd = IronsSpells.cooldown();
        if (cd > 0f) {
            Matrix4f m = g.pose().last().pose();
            float h = 12f * cd;
            rect(m, withAlpha(INK, 170), x - 6f, y + 6f - h, x + 6f, y + 6f);
        }
    }
}

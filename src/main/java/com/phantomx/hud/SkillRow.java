package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

/**
 * The other Epic Fight skills (dodge, guard, ...) as small diamonds in a row above the crest, starting just right of the
 * crest's centre. The special move is not here: it has its own always-present slot at the crest's top-left (GaugeSlots).
 * Epic Fight draws the icon itself, cropped to the diamond so nothing bleeds outside it.
 */
final class SkillRow {
    private SkillRow() {}

    private static final float HS = 12f, GAP = 1.5f;

    static void render(GuiGraphics g, Player p, int sh, float scale, float partial) {
        List<Object> all = EpicFightSkills.visible();
        if (all.isEmpty()) return;
        java.util.List<Object> skills = new java.util.ArrayList<>();
        for (Object c : all) if (!EpicFightSkills.isInnate(c)) skills.add(c);
        if (skills.isEmpty()) return;

        float crestTop = PlayerBlock.crestTopScreenY(sh, scale);
        float left = HudConfig.MARGIN_X.get() + scale * (PlayerBlock.CX + 4f);
        float cy = crestTop - HudConfig.SKILLS_Y.get() * scale - HS * scale;

        g.pose().pushPose();
        g.pose().translate(left, cy, 0);
        g.pose().scale(scale, scale, 1f);
        float x = 0f;
        for (Object c : skills) {
            float bx = x + HS;
            Matrix4f m = g.pose().last().pose();
            diamondFill(m, withAlpha(INK, 238), bx, 0f, HS, HS);
            diamondFill(m, withAlpha(argb(36, 30, 62, 255), 200), bx, 0f, HS - 2.2f, HS - 2.2f);

            float hm = HS - 1.3f;
            beginDiamondClip(g, bx, 0f, hm, HS + 18f);
            EpicFightSkills.draw(g, c, bx, 0f, hm * 1.5f, partial);
            endDiamondClip(g);

            m = g.pose().last().pose();
            diamondLine(m, GOLD, bx, 0f, HS, HS, 1.1f);
            diamondLine(m, withAlpha(PURP, 190), bx, 0f, HS - 2.6f, HS - 2.6f, 0.5f);
            x += HS * 2f + GAP;
        }
        g.pose().popPose();
    }
}

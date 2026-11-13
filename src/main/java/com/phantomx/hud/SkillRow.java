package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

/**
 * Epic Fight skills above the player block at the left edge. Every skill is the same diamond (the special move a
 * little larger); Epic Fight draws the icon itself, cropped to the diamond so nothing bleeds outside it.
 */
final class SkillRow {
    private SkillRow() {}

    private static final float BIG_HS = 13.5f, SMALL_HS = 11f, GAP = 1.5f;

    /** Draws the row and returns the screen y of its top edge (or the crest top when there is nothing to draw). */
    static float render(GuiGraphics g, Player p, int sh, float scale, float partial) {
        float crestTop = PlayerBlock.crestTopScreenY(sh, scale);
        List<Object> skills = EpicFightSkills.visible();
        if (skills.isEmpty()) return crestTop;
        float left = HudConfig.MARGIN_X.get() + scale * (PlayerBlock.CX - PlayerBlock.R);
        float cy = crestTop - HudConfig.SKILLS_Y.get() * scale - BIG_HS * scale;

        g.pose().pushPose();
        g.pose().translate(left, cy, 0);
        g.pose().scale(scale, scale, 1f);
        float x = 0f;
        for (int i = 0; i < skills.size(); i++) {
            Object c = skills.get(i);
            boolean innate = EpicFightSkills.isInnate(c);
            float hs = innate ? BIG_HS : SMALL_HS;
            float bx = x + hs;
            Matrix4f m = g.pose().last().pose();
            diamondFill(m, withAlpha(INK, 238), bx, 0f, hs, hs);
            diamondFill(m, withAlpha(argb(36, 30, 62, 255), 200), bx, 0f, hs - 2.2f, hs - 2.2f);

            float hm = hs - 1.3f;
            beginDiamondClip(g, bx, 0f, hm, hs + 18f);
            EpicFightSkills.draw(g, c, bx, 0f, hm * 1.5f, partial);
            endDiamondClip(g);

            m = g.pose().last().pose();
            diamondLine(m, GOLD, bx, 0f, hs, hs, 1.1f);
            diamondLine(m, withAlpha(PURP, 190), bx, 0f, hs - 2.6f, hs - 2.6f, 0.5f);
            if (innate) {
                diamondFill(m, GOLD, bx - hs - 1.2f, 0f, 1.7f, 1.7f);
                diamondFill(m, GOLD, bx + hs + 1.2f, 0f, 1.7f, 1.7f);
            }
            x += hs * 2f + GAP;
        }
        g.pose().popPose();
        return cy - (BIG_HS + 2f) * scale;
    }
}

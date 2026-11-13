package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

/**
 * Epic Fight skills above the player block at the left edge: the special move icon large and fixed in place (Epic
 * Fight's own icon), then the other skills as small diamonds.
 */
final class SkillRow {
    private SkillRow() {}

    private static final float BIG = 28f, SMALL_HS = 11f;

    /** Draws the row and returns the screen y of its top edge (or the crest top when there is nothing to draw). */
    static float render(GuiGraphics g, Player p, int sh, float scale, float partial) {
        float crestTop = PlayerBlock.crestTopScreenY(sh, scale);
        List<Object> skills = EpicFightSkills.visible();
        if (skills.isEmpty()) return crestTop;
        float left = HudConfig.MARGIN_X.get() + scale * (PlayerBlock.CX - PlayerBlock.R);
        float cy = crestTop - HudConfig.SKILLS_Y.get() * scale - BIG / 2f * scale;

        g.pose().pushPose();
        g.pose().translate(left, cy, 0);
        g.pose().scale(scale, scale, 1f);
        float x = 0f;
        for (int i = 0; i < skills.size(); i++) {
            Object c = skills.get(i);
            Matrix4f m = g.pose().last().pose();
            if (EpicFightSkills.isInnate(c)) {
                float bx = x + BIG / 2f;
                bracket(m, bx, 0f);
                EpicFightSkills.draw(g, c, bx, 0f, BIG, partial);
                x += BIG + 5f;
            } else {
                float bx = x + SMALL_HS;
                diamondFill(m, withAlpha(INK, 230), bx, 0, SMALL_HS, SMALL_HS);
                diamondLine(m, GOLD, bx, 0, SMALL_HS, SMALL_HS, 1.1f);
                diamondLine(m, withAlpha(PURP, 190), bx, 0, SMALL_HS - 2.8f, SMALL_HS - 2.8f, 0.5f);
                EpicFightSkills.draw(g, c, bx, 0f, 14f, partial);
                x += SMALL_HS * 2f + 1.5f;
            }
        }
        g.pose().popPose();
        return cy - (BIG / 2f + 2f) * scale;
    }

    /** Thin gold corner marks around the special move icon. */
    private static void bracket(Matrix4f m, float cx, float cy) {
        float h = BIG / 2f + 2f, l = 5f;
        int c = withAlpha(GOLD, 230);
        line(m, c, cx - h, cy - h, cx - h + l, cy - h, 0.9f);
        line(m, c, cx - h, cy - h, cx - h, cy - h + l, 0.9f);
        line(m, c, cx + h, cy - h, cx + h - l, cy - h, 0.9f);
        line(m, c, cx + h, cy - h, cx + h, cy - h + l, 0.9f);
        line(m, c, cx - h, cy + h, cx - h + l, cy + h, 0.9f);
        line(m, c, cx - h, cy + h, cx - h, cy + h - l, 0.9f);
        line(m, c, cx + h, cy + h, cx + h - l, cy + h, 0.9f);
        line(m, c, cx + h, cy + h, cx + h, cy + h - l, 0.9f);
    }
}

package com.phantomx.hud;

import static com.phantomx.hud.HudDraw.*;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

/** Epic Fight skills as a row of small diamonds above the player block, at the left edge. */
final class SkillRow {
    private SkillRow() {}

    private static final float HS = 12f;

    static void render(GuiGraphics g, Player p, int sh, float scale, float partial) {
        List<Object> skills = EpicFightSkills.visible();
        if (skills.isEmpty()) return;
        float x0 = HudConfig.MARGIN_X.get() + scale * (PlayerBlock.CX - PlayerBlock.R);
        float cy = PlayerBlock.crestTopScreenY(sh, scale) - HudConfig.SKILLS_Y.get() * scale;
        g.pose().pushPose();
        g.pose().translate(x0 + HS * scale, cy, 0);
        g.pose().scale(scale, scale, 1f);
        for (int i = 0; i < skills.size(); i++) {
            float x = i * (HS * 2f + 1.5f);
            Matrix4f m = g.pose().last().pose();
            diamondFill(m, withAlpha(INK, 230), x, 0, HS, HS);
            diamondLine(m, GOLD, x, 0, HS, HS, 1.1f);
            diamondLine(m, withAlpha(PURP, 190), x, 0, HS - 2.8f, HS - 2.8f, 0.5f);
            EpicFightSkills.draw(g, skills.get(i), x, 0, 14f, partial);
        }
        g.pose().popPose();
    }
}

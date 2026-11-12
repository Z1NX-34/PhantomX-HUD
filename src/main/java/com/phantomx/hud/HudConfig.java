package com.phantomx.hud;

import net.minecraftforge.common.ForgeConfigSpec;

public final class HudConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED, HIDE_OTHER_HUDS, SHOW_EFFECTS, SHOW_XP, SHOW_CHIPS;
    public static final ForgeConfigSpec.DoubleValue SCALE;
    public static final ForgeConfigSpec.IntValue MARGIN_X, MARGIN_Y, EFFECTS_Y;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("hud");
        ENABLED = b.comment("Master switch. false = PhantomX HUD draws nothing and the normal HUD comes back.").define("enabled", true);
        HIDE_OTHER_HUDS = b.comment("Also hide Souls Combat HUD's player gauge and XP overlay while enabled (only matters if that mod is installed).").define("hide_souls_combat_hud_overlays", true);
        SCALE = b.comment("Size multiplier.").defineInRange("scale", 1.0, 0.5, 2.5);
        MARGIN_X = b.comment("Player block distance from the left edge (GUI pixels).").defineInRange("margin_x", 10, 0, 400);
        MARGIN_Y = b.comment("Player block distance from the bottom edge (GUI pixels).").defineInRange("margin_y", 6, 0, 400);
        SHOW_CHIPS = b.comment("Armor and food chips under the crest.").define("show_armor_food", true);
        SHOW_XP = b.comment("Short centred XP bar above the hotbar.").define("show_xp_bar", true);
        SHOW_EFFECTS = b.comment("Potion effect strip on the right edge.").define("show_effects", true);
        EFFECTS_Y = b.comment("Effect strip distance from the bottom edge (GUI pixels).").defineInRange("effects_y", 120, 0, 600);
        b.pop();
        SPEC = b.build();
    }

    private HudConfig() {}
}

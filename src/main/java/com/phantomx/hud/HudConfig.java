package com.phantomx.hud;

import net.minecraftforge.common.ForgeConfigSpec;

public final class HudConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED, HIDE_OTHER_HUDS;
    public static final ForgeConfigSpec.BooleanValue SHOW_CHIPS, SHOW_XP, SHOW_EFFECTS, SHOW_SKILLS, SHOW_EQUIPMENT, SHOW_BOSS, SHOW_COMPASS;
    public static final ForgeConfigSpec.BooleanValue COMPASS_MARKERS, COMPASS_DEATHS;
    public static final ForgeConfigSpec.DoubleValue SCALE;
    public static final ForgeConfigSpec.IntValue MARGIN_X, MARGIN_Y, EFFECTS_Y, SKILLS_Y, EQUIP_X, EQUIP_Y;
    public static final ForgeConfigSpec.IntValue COMPASS_WIDTH, COMPASS_RANGE, COMPASS_VERTICAL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("hud");
        ENABLED = b.comment("Master switch. false = PhantomX HUD draws nothing and the normal HUD comes back.").define("enabled", true);
        HIDE_OTHER_HUDS = b.comment("Also hide Souls Combat HUD's player gauge, XP, skill, equipment and boss overlays (only matters if that mod is installed).").define("hide_souls_combat_hud_overlays", true);
        SCALE = b.comment("Size multiplier.").defineInRange("scale", 1.0, 0.5, 2.5);
        b.pop();
        b.push("player");
        MARGIN_X = b.comment("Player block distance from the left edge (GUI pixels).").defineInRange("margin_x", 10, 0, 400);
        MARGIN_Y = b.comment("Player block distance from the bottom edge (GUI pixels).").defineInRange("margin_y", 6, 0, 400);
        SHOW_CHIPS = b.comment("Armor and food chips under the crest.").define("show_armor_food", true);
        b.pop();
        b.push("xp");
        SHOW_XP = b.comment("Short centred XP bar above the hotbar.").define("show_xp_bar", true);
        b.pop();
        b.push("effects");
        SHOW_EFFECTS = b.comment("Potion effect strip on the right edge.").define("show_effects", true);
        EFFECTS_Y = b.comment("Effect strip distance from the bottom edge (GUI pixels).").defineInRange("effects_y", 130, 0, 600);
        b.pop();
        b.push("skills");
        SHOW_SKILLS = b.comment("Epic Fight skill diamonds above the player block (needs Epic Fight).").define("show_skills", true);
        SKILLS_Y = b.comment("Skill row distance above the crest top (GUI pixels).").defineInRange("skills_gap", 14, 0, 300);
        b.pop();
        b.push("equipment");
        SHOW_EQUIPMENT = b.comment("Four diamond slots at the bottom-right: weapon, offhand, consumable, spell.").define("show_equipment", true);
        EQUIP_X = b.comment("Slot cluster centre, distance from the right edge.").defineInRange("equipment_x", 48, 0, 400);
        EQUIP_Y = b.comment("Slot cluster centre, distance from the bottom edge.").defineInRange("equipment_y", 52, 0, 400);
        b.pop();
        b.push("boss");
        SHOW_BOSS = b.comment("Custom boss bars (replaces the vanilla ones).").define("show_boss_bars", true);
        b.pop();
        b.push("compass");
        SHOW_COMPASS = b.comment("Thin compass at the top. Also hides the Advanced Compass mod's own bar while on.").define("show_compass", true);
        COMPASS_WIDTH = b.comment("Compass width (GUI pixels).").defineInRange("compass_width", 300, 100, 800);
        COMPASS_MARKERS = b.comment("Show Remnants Minimap markers on the compass (needs Remnants Minimap).").define("compass_remnant_markers", true);
        COMPASS_DEATHS = b.comment("Include death markers.").define("compass_show_deaths", true);
        COMPASS_RANGE = b.comment("Markers farther than this (blocks) are hidden. 0 = no limit.").defineInRange("compass_marker_range", 0, 0, 1000000);
        COMPASS_VERTICAL = b.comment("Markers with a known height more than this many blocks above/below you are hidden (underground markers). 0 = off.").defineInRange("compass_marker_vertical_limit", 0, 0, 1000);
        b.pop();
        SPEC = b.build();
    }

    private HudConfig() {}
}

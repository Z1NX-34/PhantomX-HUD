package com.phantomx.hud;

import net.minecraftforge.common.ForgeConfigSpec;

public final class HudConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED, HIDE_OTHER_HUDS;
    public static final ForgeConfigSpec.BooleanValue SHOW_CHIPS, SHOW_XP, SHOW_EFFECTS, SHOW_SKILLS, SHOW_BOSS, SHOW_COMPASS;
    public static final ForgeConfigSpec.BooleanValue COMPASS_MARKERS, COMPASS_DEATHS, SHOW_MOUNT, MOVE_CHAT, AUTO_HIDE_BOSS, BUILTIN_HIDE, SHOW_HOTBAR;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> HIDE_OVERLAYS;
    public static final ForgeConfigSpec.IntValue CHAT_SHIFT_X, CHAT_SHIFT_Y, ITEM_NAME_RAISE;
    public static final ForgeConfigSpec.DoubleValue SCALE;
    public static final ForgeConfigSpec.IntValue MARGIN_X, MARGIN_Y, SKILLS_Y, MODULE_MARGIN_X, MODULE_MARGIN_Y, EFFECTS_OFFSET_Y, BOSS_MAX;
    public static final ForgeConfigSpec.DoubleValue MODULE_SCALE;
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
        SHOW_CHIPS = b.comment("The three refill diamonds (mana, armor, hunger) above the health bar.").define("show_armor_food", true);
        SHOW_MOUNT = b.comment("Health bar of the mount you ride (centre, above the jump / charge bar). Also hides the vanilla / modded mount health overlay.").define("show_mount_bar", true);
        b.pop();
        b.push("xp");
        SHOW_XP = b.comment("XP bar wrapped around the left side of the bottom-right diamond module.").define("show_xp_bar", true);
        b.pop();
        b.push("hotbar");
        SHOW_HOTBAR = b.comment("Replace the vanilla hotbar with the diamond slot module at the bottom-right.").define("show_hotbar", true);
        MODULE_MARGIN_X = b.comment("Diamond module distance from the right edge (GUI pixels).").defineInRange("module_margin_x", 8, 0, 400);
        MODULE_MARGIN_Y = b.comment("Diamond module distance from the bottom edge (GUI pixels).").defineInRange("module_margin_y", 8, 0, 400);
        MODULE_SCALE = b.comment("Size multiplier of the diamond module only (on top of the general scale).").defineInRange("module_scale", 1.0, 0.5, 2.0);
        b.pop();
        b.push("effects");
        SHOW_EFFECTS = b.comment("Potion effect tiles on the right edge.").define("show_effects", true);
        EFFECTS_OFFSET_Y = b.comment("Effect tabs are centred vertically on the right edge; this moves them down (negative = up), in GUI pixels. Seven per column, more columns grow left.").defineInRange("effects_offset_y", 0, -300, 300);
        b.pop();
        b.push("skills");
        SHOW_SKILLS = b.comment("Epic Fight special move slot (top-left of the crest, always shown) and the other skill diamonds above the crest.").define("show_skills", true);
        SKILLS_Y = b.comment("Gap between the crest top and the row of the other Epic Fight skills (GUI pixels).").defineInRange("skills_gap", 10, 0, 300);
        b.pop();
        b.push("boss");
        SHOW_BOSS = b.comment("Replace every vanilla-style boss bar (including other mods' custom bosses that use boss events) with the PhantomX one.").define("show_boss_bars", true);
        AUTO_HIDE_BOSS = b.comment("Also hide other mods' own boss bar overlays (any overlay whose id contains 'boss'). Intro / title overlays are left alone.").define("hide_other_boss_overlays", true);
        BOSS_MAX = b.comment("Maximum number of boss bars shown at once.").defineInRange("max_boss_bars", 3, 1, 8);
        b.pop();
        b.push("compass");
        SHOW_COMPASS = b.comment("Thin compass at the top. Also hides the Advanced Compass mod's own bar while on.").define("show_compass", true);
        COMPASS_WIDTH = b.comment("Compass width (GUI pixels).").defineInRange("compass_width", 300, 100, 800);
        COMPASS_MARKERS = b.comment("Show Remnants Minimap markers on the compass (needs Remnants Minimap).").define("compass_remnant_markers", true);
        COMPASS_DEATHS = b.comment("Include death markers.").define("compass_show_deaths", true);
        COMPASS_RANGE = b.comment("Markers farther than this (blocks) are hidden. 0 = no limit.").defineInRange("compass_marker_range", 0, 0, 1000000);
        COMPASS_VERTICAL = b.comment("Markers with a known height more than this many blocks above/below you are hidden (underground markers). 0 = off.").defineInRange("compass_marker_vertical_limit", 0, 0, 1000);
        b.pop();
        b.push("layout");
        HIDE_OVERLAYS = b.comment("Other mods' HUD overlays to hide while PhantomX HUD is on. 'modid:*' hides every overlay of a mod, a trailing * matches a prefix.")
                .defineList("hide_overlays", java.util.List.of(), o -> o instanceof String);
        BUILTIN_HIDE = b.comment("Always hide Epic Fight's HUD and Iron's Spells' mana bar / spell slots, even if the list above is an old one. The list is only additional.").define("hide_builtin_mod_overlays", true);
        MOVE_CHAT = b.comment("Move the chat out of the way of the player block and the equipment slots.").define("move_chat", false);
        CHAT_SHIFT_X = b.comment("How far the chat moves right (GUI pixels).").defineInRange("chat_shift_x", 205, 0, 800);
        CHAT_SHIFT_Y = b.comment("How far the chat moves up (GUI pixels).").defineInRange("chat_shift_y", 24, 0, 400);
        ITEM_NAME_RAISE = b.comment("How far the held-item name moves up (GUI pixels).").defineInRange("item_name_raise", 0, 0, 100);
        b.pop();
        SPEC = b.build();
    }

    private HudConfig() {}
}

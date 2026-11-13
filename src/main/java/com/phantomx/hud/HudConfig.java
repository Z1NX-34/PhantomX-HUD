package com.phantomx.hud;

import net.minecraftforge.common.ForgeConfigSpec;

public final class HudConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED, HIDE_OTHER_HUDS;
    public static final ForgeConfigSpec.BooleanValue SHOW_CHIPS, SHOW_XP, SHOW_EFFECTS, SHOW_SKILLS, SHOW_EQUIPMENT, SHOW_BOSS, SHOW_COMPASS;
    public static final ForgeConfigSpec.BooleanValue COMPASS_MARKERS, COMPASS_DEATHS, MOVE_CHAT, AUTO_HIDE_BOSS, BUILTIN_HIDE;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> HIDE_OVERLAYS;
    public static final ForgeConfigSpec.IntValue CHAT_SHIFT_X, CHAT_SHIFT_Y, ITEM_NAME_RAISE;
    public static final ForgeConfigSpec.DoubleValue SCALE;
    public static final ForgeConfigSpec.IntValue MARGIN_X, MARGIN_Y, SKILLS_Y, EQUIP_X, EQUIP_Y, EFFECTS_TOP, BOSS_MAX;
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
        SHOW_EFFECTS = b.comment("Potion effect tiles on the right edge.").define("show_effects", true);
        EFFECTS_TOP = b.comment("Effect tiles: distance of the first row from the TOP edge (GUI pixels). Rows stack downward, extra columns grow left.").defineInRange("effects_top", 44, 0, 600);
        b.pop();
        b.push("skills");
        SHOW_SKILLS = b.comment("Epic Fight special move + skill diamonds above the player block (needs Epic Fight).").define("show_skills", true);
        SKILLS_Y = b.comment("Skill row distance above the crest top (GUI pixels).").defineInRange("skills_gap", 10, 0, 300);
        b.pop();
        b.push("equipment");
        SHOW_EQUIPMENT = b.comment("Four diamond slots at the bottom-right: weapon, offhand, consumable, spell.").define("show_equipment", true);
        EQUIP_X = b.comment("Slot cluster centre, distance right of the hotbar's right edge (GUI pixels).").defineInRange("equipment_offset_x", 112, 0, 400);
        EQUIP_Y = b.comment("Slot cluster centre, distance from the bottom edge (GUI pixels).").defineInRange("equipment_y", 34, 0, 400);
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
                .defineList("hide_overlays", java.util.List.of("epicfight:*", "irons_spellbooks:mana*", "irons_spellbooks:spell*"), o -> o instanceof String);
        BUILTIN_HIDE = b.comment("Always hide Epic Fight's HUD and Iron's Spells' mana bar / spell slots, even if the list above is an old one. The list is only additional.").define("hide_builtin_mod_overlays", true);
        MOVE_CHAT = b.comment("Move the chat out of the way of the player block and the equipment slots.").define("move_chat", false);
        CHAT_SHIFT_X = b.comment("How far the chat moves right (GUI pixels).").defineInRange("chat_shift_x", 205, 0, 800);
        CHAT_SHIFT_Y = b.comment("How far the chat moves up (GUI pixels).").defineInRange("chat_shift_y", 24, 0, 400);
        ITEM_NAME_RAISE = b.comment("How far the held-item name moves up so it clears the XP bar (GUI pixels).").defineInRange("item_name_raise", 16, 0, 100);
        b.pop();
        SPEC = b.build();
    }

    private HudConfig() {}
}

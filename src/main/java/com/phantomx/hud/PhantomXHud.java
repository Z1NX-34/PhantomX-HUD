package com.phantomx.hud;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(PhantomXHud.MODID)
public final class PhantomXHud {
    public static final String MODID = "phantomxhud";

    public PhantomXHud() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, HudConfig.SPEC, "phantomx_hud-client.toml");
    }
}

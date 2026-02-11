package com.spectate.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

/**
 * Mod Menu integration entrypoint.
 * Returns a Cloth Config screen factory when Cloth Config is available.
 */
@Environment(EnvType.CLIENT)
public class SpectateModMenuApi implements ModMenuApi {

    @Override
    @Nullable
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!isClothConfigLoaded()) {
            return null;
        }
        return SpectateConfigScreenFactory::create;
    }

    private boolean isClothConfigLoaded() {
        FabricLoader loader = FabricLoader.getInstance();
        return loader.isModLoaded("cloth-config") || loader.isModLoaded("cloth-config2");
    }
}


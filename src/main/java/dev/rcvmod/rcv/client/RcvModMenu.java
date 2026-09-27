package dev.rcvmod.rcv.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/** ModMenu integration: exposes the Cloth Config screen from the mod list. */
public final class RcvModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Keep the Cloth-dependent class out of the method-reference bootstrap so ModMenu can load
        // this entrypoint even when Cloth Config is missing.
        return parent -> {
            if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
                return null;
            }
            return RcvConfigScreen.create(parent);
        };
    }
}

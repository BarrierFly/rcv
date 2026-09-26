package dev.rcvmod.rcv;

import dev.rcvmod.rcv.command.RcvServerCommand;
import dev.rcvmod.rcv.config.RcvServerConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RCV implements ModInitializer {

    public static final String MOD_ID = "rcv";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        RcvServerConfig.get();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // On singleplayer the client command owns /rcv; on dedicated servers this serves vanilla clients.
            if (environment == Commands.CommandSelection.DEDICATED) {
                RcvServerCommand.register(dispatcher);
            }
        });
        RCV.LOGGER.info("RCV — Redstone Connection Visualized initialized");
    }
}

package dev.rcvmod.rcv.client;

import dev.rcvmod.rcv.config.RcvConfig;
import dev.rcvmod.rcv.version.HudCompat;
import dev.rcvmod.rcv.version.RenderCompat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class RcvClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        RcvConfig.get();
        RenderCompat.register();
        HudCompat.register();
        RcvWand.register();
        RcvClientCommand.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> RcvClientState.tick());
    }
}

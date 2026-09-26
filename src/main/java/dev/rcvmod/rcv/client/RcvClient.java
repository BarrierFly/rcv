package dev.rcvmod.rcv.client;

import dev.rcvmod.rcv.config.RcvConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class RcvClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        RcvConfig.get();
        RcvGraphRenderer.register();
        RcvHud.register();
        RcvWand.register();
        RcvClientCommand.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> RcvClientState.tick());
    }
}

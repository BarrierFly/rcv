package dev.rcvmod.rcv.version;

import dev.rcvmod.rcv.client.RcvHud;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public final class HudCompat {

    private HudCompat() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> RcvHud.render(graphics));
    }
}

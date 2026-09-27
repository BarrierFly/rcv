package dev.rcvmod.rcv.version;

/**
 * 26.x moved the HUD to the new extract-based API ({@code HudElement#extractRenderState} with
 * {@code GuiGraphicsExtractor}), which RCV does not target yet. Chat feedback still works.
 */
public final class HudCompat {

    private HudCompat() {
    }

    public static void register() {
    }
}

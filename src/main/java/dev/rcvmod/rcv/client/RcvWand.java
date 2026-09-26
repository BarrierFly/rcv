package dev.rcvmod.rcv.client;

import dev.rcvmod.rcv.config.RcvConfig;
import dev.rcvmod.rcv.core.Region;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** "Magic wand" using the configured item to pick an origin or a region (§7.1/§7.2). */
public final class RcvWand {

    private RcvWand() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND || !isWand(player)
                    || !RcvClientState.wandEnabled) {
                return InteractionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            if (RcvClientState.regionSelectionMode) {
                if (player.isShiftKeyDown()) {
                    RcvClientState.pos1 = null;
                    RcvClientState.pos2 = null;
                    RcvClientState.setRegion(null);
                    player.displayClientMessage(Component.literal("RCV: region cleared"), true);
                } else {
                    RcvClientState.pos2 = pos.immutable();
                    if (RcvClientState.pos1 != null) {
                        RcvClientState.setRegion(Region.of(RcvClientState.pos1, RcvClientState.pos2));
                        player.displayClientMessage(Component.literal("RCV: region " + RcvClientState.pos1.toShortString()
                                + " .. " + RcvClientState.pos2.toShortString()), true);
                    } else {
                        player.displayClientMessage(Component.literal("RCV: pos2 " + pos.toShortString()
                                + " (need pos1 with left click)"), true);
                    }
                }
            } else {
                RcvClientState.setOriginAndCompute(pos);
                player.displayClientMessage(Component.literal("RCV " + RcvClientState.mode().name().toLowerCase()
                        + " from " + pos.toShortString()), true);
            }
            return InteractionResult.SUCCESS;
        });

        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND || !isWand(player)
                    || !RcvClientState.wandEnabled || !RcvClientState.regionSelectionMode) {
                return InteractionResult.PASS;
            }
            RcvClientState.pos1 = pos.immutable();
            player.displayClientMessage(Component.literal("RCV: pos1 " + pos.toShortString()), true);
            return InteractionResult.SUCCESS;
        });
    }

    private static boolean isWand(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return false;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id.equals(RcvConfig.get().wandItem);
    }
}

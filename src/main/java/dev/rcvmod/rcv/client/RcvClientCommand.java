package dev.rcvmod.rcv.client;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.rcvmod.rcv.command.RcvCommandUtil;
import dev.rcvmod.rcv.config.RcvConfig;
import dev.rcvmod.rcv.core.ComponentCatalog;
import dev.rcvmod.rcv.core.ConnectionGraph;
import dev.rcvmod.rcv.core.GraphOptions;
import dev.rcvmod.rcv.core.QueryMode;
import dev.rcvmod.rcv.core.Region;
import dev.rcvmod.rcv.core.TypeMask;
import dev.rcvmod.rcv.mc.LevelWorldView;
import dev.rcvmod.rcv.version.ClientCompat;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/** Client-side {@code /rcv}: computes from the client level and drives the overlay (C only / S+C). */
public final class RcvClientCommand {

    private RcvClientCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCompat.literal("rcv")
                        .then(query("in", QueryMode.IN))
                        .then(query("out", QueryMode.OUT))
                        .then(ClientCompat.literal("clear").executes(ctx -> {
                            RcvClientState.clear();
                            ctx.getSource().sendFeedback(Component.translatable("rcv.command.clear"));
                            return 1;
                        }))
                        .then(ClientCompat.literal("refresh").executes(ctx -> {
                            RcvClientState.refresh();
                            ctx.getSource().sendFeedback(Component.translatable("rcv.command.refresh"));
                            return 1;
                        }))
                        .then(ClientCompat.literal("types").executes(ctx -> {
                            ctx.getSource().sendFeedback(Component.translatable("rcv.types.header"));
                            ctx.getSource().sendFeedback(RcvCommandUtil.typeList());
                            return 1;
                        }))
                        .then(ClientCompat.literal("config").executes(ctx -> {
                            if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cloth-config")) {
                                ctx.getSource().sendError(Component.literal(
                                        "RCV: Cloth Config is required for the config GUI"));
                                return 0;
                            }
                            ClientCompat.openScreen(RcvConfigScreen.create(null));
                            return 1;
                        }))
                        .then(ClientCompat.literal("mode")
                                .then(ClientCompat.literal("in").executes(ctx -> {
                                    RcvClientState.setMode(QueryMode.IN);
                                    ctx.getSource().sendFeedback(Component.literal("RCV mode = in"));
                                    return 1;
                                }))
                                .then(ClientCompat.literal("out").executes(ctx -> {
                                    RcvClientState.setMode(QueryMode.OUT);
                                    ctx.getSource().sendFeedback(Component.literal("RCV mode = out"));
                                    return 1;
                                })))
                        .then(ClientCompat.literal("depth")
                                .then(ClientCompat.argument("value", IntegerArgumentType.integer(1, GraphOptions.MAX_DEPTH))
                                        .executes(ctx -> {
                                            int value = IntegerArgumentType.getInteger(ctx, "value");
                                            RcvClientState.setDepth(value);
                                            ctx.getSource().sendFeedback(Component.literal("RCV depth = " + value));
                                            return 1;
                                        })))
                        .then(ClientCompat.literal("wand")
                                .then(ClientCompat.argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
                                    boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                    RcvClientState.wandEnabled = enabled;
                                    ctx.getSource().sendFeedback(Component.literal("RCV wand = " + enabled));
                                    return 1;
                                })))
                        .then(ClientCompat.literal("region")
                                .then(ClientCompat.literal("mode")
                                        .then(ClientCompat.argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
                                            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                            RcvClientState.regionSelectionMode = enabled;
                                            ctx.getSource().sendFeedback(
                                                    Component.literal("RCV region mode = " + enabled));
                                            return 1;
                                        })))
                                .then(ClientCompat.literal("clear").executes(ctx -> {
                                    RcvClientState.pos1 = null;
                                    RcvClientState.pos2 = null;
                                    RcvClientState.setRegion(null);
                                    ctx.getSource().sendFeedback(Component.literal("RCV region cleared"));
                                    return 1;
                                })))
                        .then(ClientCompat.literal("colorblind")
                                .then(ClientCompat.argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
                                    RcvConfig config = RcvConfig.get();
                                    config.alternatePalette = BoolArgumentType.getBool(ctx, "enabled");
                                    config.save();
                                    ctx.getSource().sendFeedback(
                                            Component.literal("RCV colorblind palette = " + config.alternatePalette));
                                    return 1;
                                })))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> query(
            String name, QueryMode mode) {
        return ClientCompat.literal(name).then(ClientCompat.argument("pos", new ClientPosArgument())
                .executes(ctx -> run(ctx, mode, RcvConfig.get().defaultDepth, ""))
                .then(ClientCompat.argument("depth", IntegerArgumentType.integer(1, GraphOptions.MAX_DEPTH))
                        .executes(ctx -> run(ctx, mode, IntegerArgumentType.getInteger(ctx, "depth"), ""))
                        .then(ClientCompat.argument("options", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx, mode, IntegerArgumentType.getInteger(ctx, "depth"),
                                        StringArgumentType.getString(ctx, "options"))))));
    }

    private static int run(CommandContext<FabricClientCommandSource> ctx, QueryMode mode, int depth, String rawOptions)
            throws CommandSyntaxException {
        FabricClientCommandSource source = ctx.getSource();
        ClientPosArgument.Pos pos = ctx.getArgument("pos", ClientPosArgument.Pos.class);
        BlockPos origin = BlockPos.containing(pos.resolve(source.getPosition()));
        RcvConfig config = RcvConfig.get();
        RcvCommandUtil.Parsed parsed = RcvCommandUtil.parse(rawOptions, depth, config.typeMask());

        LevelWorldView world = new LevelWorldView(ClientCompat.world(source));
        BlockState state = world.state(origin);
        boolean component = ComponentCatalog.isComponent(state);
        boolean conductor = world.isConductor(origin);
        if (!component && !conductor) {
            source.sendError(Component.translatable("rcv.command.not_component"));
            return 0;
        }
        if (mode == QueryMode.OUT && conductor && !component) {
            source.sendError(Component.translatable("rcv.command.out_conductor"));
            return 0;
        }
        if (parsed.region() != null && !parsed.region().contains(origin)) {
            source.sendError(Component.translatable("rcv.command.origin_outside_region"));
            return 0;
        }

        RcvClientState.runQuery(mode, origin, parsed.depth(), parsed.mask(), parsed.region());
        ConnectionGraph graph = RcvClientState.graph();
        if (graph == null || graph.edgeCount() == 0) {
            source.sendFeedback(Component.translatable("rcv.command.empty", parsed.depth()));
            return 1;
        }
        source.sendFeedback(Component.translatable(mode == QueryMode.IN ? "rcv.command.in.header"
                : "rcv.command.out.header", origin.toShortString()));
        source.sendFeedback(Component.translatable("rcv.command.summary", graph.nodeCount(), graph.edgeCount(),
                parsed.depth()));
        RcvCommandUtil.edgeLines(graph).forEach(source::sendFeedback);
        if (graph.truncated()) {
            source.sendFeedback(Component.translatable("rcv.command.truncated", graph.nodeCount(), graph.edgeCount()));
        }
        return 1;
    }
}



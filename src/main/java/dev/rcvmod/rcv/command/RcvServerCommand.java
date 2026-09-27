package dev.rcvmod.rcv.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.rcvmod.rcv.config.RcvServerConfig;
import dev.rcvmod.rcv.core.ComponentCatalog;
import dev.rcvmod.rcv.core.ConnectionEngine;
import dev.rcvmod.rcv.core.ConnectionGraph;
import dev.rcvmod.rcv.core.EdgeType;
import dev.rcvmod.rcv.core.GraphEdge;
import dev.rcvmod.rcv.core.GraphNode;
import dev.rcvmod.rcv.core.GraphOptions;
import dev.rcvmod.rcv.core.QueryMode;
import dev.rcvmod.rcv.mc.LevelWorldView;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import dev.rcvmod.rcv.version.ServerCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Server-side {@code /rcv}, used for {@code S only} (vanilla clients) and as authoritative compute. */
public final class RcvServerCommand {

    private record QueryState(QueryMode mode, BlockPos pos, int depth, String options) {
    }

    private static final Map<UUID, QueryState> LAST_QUERY = new HashMap<>();

    private RcvServerCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rcv")
                .requires(RcvServerCommand::allowed)
                .then(Commands.literal("in").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> run(ctx, QueryMode.IN, RcvServerConfig.get().defaultDepth, ""))
                        .then(Commands.argument("depth", IntegerArgumentType.integer(1, GraphOptions.MAX_DEPTH))
                                .executes(ctx -> run(ctx, QueryMode.IN, IntegerArgumentType.getInteger(ctx, "depth"), ""))
                                .then(Commands.argument("options", StringArgumentType.greedyString())
                                        .executes(ctx -> run(ctx, QueryMode.IN,
                                                IntegerArgumentType.getInteger(ctx, "depth"),
                                                StringArgumentType.getString(ctx, "options")))))))
                .then(Commands.literal("out").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> run(ctx, QueryMode.OUT, RcvServerConfig.get().defaultDepth, ""))
                        .then(Commands.argument("depth", IntegerArgumentType.integer(1, GraphOptions.MAX_DEPTH))
                                .executes(ctx -> run(ctx, QueryMode.OUT, IntegerArgumentType.getInteger(ctx, "depth"), ""))
                                .then(Commands.argument("options", StringArgumentType.greedyString())
                                        .executes(ctx -> run(ctx, QueryMode.OUT,
                                                IntegerArgumentType.getInteger(ctx, "depth"),
                                                StringArgumentType.getString(ctx, "options")))))))
                .then(Commands.literal("clear").executes(RcvServerCommand::clear))
                .then(Commands.literal("refresh").executes(RcvServerCommand::refresh))
                .then(Commands.literal("reload").executes(RcvServerCommand::reload))
                .then(Commands.literal("types").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.translatable("rcv.types.header"), false);
                    ctx.getSource().sendSuccess(RcvCommandUtil::typeList, false);
                    return 1;
                })));
    }

    private static boolean allowed(CommandSourceStack source) {
        RcvServerConfig config = RcvServerConfig.get();
        if (!config.enabled) {
            return false;
        }
        if (!config.requireOp || config.allowNonOp) {
            return true;
        }
        return ServerCompat.hasGamemaster(source);
    }

    private static int run(CommandContext<CommandSourceStack> ctx, QueryMode mode, int depth, String rawOptions)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        RcvServerConfig config = RcvServerConfig.get();
        BlockPos origin = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        RcvCommandUtil.Parsed parsed = RcvCommandUtil.parse(rawOptions, Math.min(depth, config.maxDepth),
                dev.rcvmod.rcv.core.TypeMask.all());

        ServerLevel level = source.getLevel();
        LevelWorldView world = new LevelWorldView(level);
        BlockState originState = world.state(origin);
        boolean component = ComponentCatalog.isComponent(originState);
        boolean conductor = world.isConductor(origin);

        if (!component && !conductor) {
            source.sendFailure(Component.translatable("rcv.command.not_component"));
            return 0;
        }
        if (mode == QueryMode.OUT && conductor && !component) {
            source.sendFailure(Component.translatable("rcv.command.out_conductor"));
            return 0;
        }
        if (parsed.region() != null && !parsed.region().contains(origin)) {
            source.sendFailure(Component.translatable("rcv.command.origin_outside_region"));
            return 0;
        }

        GraphOptions options = new GraphOptions(parsed.depth(), parsed.mask(), config.ncMode(), config.ppMode(),
                parsed.region(), config.maxNodes, config.maxEdges, config.railRange);
        ConnectionGraph graph = new ConnectionEngine(world, options, mode).compute(origin);

        if (graph.edgeCount() == 0) {
            source.sendSuccess(() -> Component.translatable("rcv.command.empty", parsed.depth()), false);
        } else {
            source.sendSuccess(() -> Component.translatable(mode == QueryMode.IN ? "rcv.command.in.header"
                    : "rcv.command.out.header", origin.toShortString()), false);
            source.sendSuccess(() -> Component.translatable("rcv.command.summary", graph.nodeCount(), graph.edgeCount(),
                    parsed.depth()), false);
            RcvCommandUtil.edgeLines(graph).forEach(line -> source.sendSuccess(() -> line, false));
            if (graph.truncated()) {
                source.sendSuccess(() -> Component.translatable("rcv.command.truncated", graph.nodeCount(),
                        graph.edgeCount()), false);
            }
            renderParticles(level, graph, parsed.mask());
        }

        if (source.getPlayer() != null) {
            LAST_QUERY.put(source.getPlayer().getUUID(), new QueryState(mode, origin, parsed.depth(), rawOptions));
        }
        return 1;
    }

    private static void renderParticles(ServerLevel level, ConnectionGraph graph,
                                        dev.rcvmod.rcv.core.TypeMask mask) {
        for (GraphEdge edge : graph.edges()) {
            if (!mask.allows(edge.type)) {
                continue;
            }
            GraphNode from = graph.node(edge.from);
            GraphNode to = graph.node(edge.to);
            if (from == null || to == null) {
                continue;
            }
            int color = 0xFF0000 | ((edge.type.ordinal() * 0x1F1F1F) & 0x00FFFF);
            ParticleOptions particle = ServerCompat.dust(color);
            Vec3 a = Vec3.atCenterOf(from.pos);
            Vec3 b = Vec3.atCenterOf(to.pos);
            for (int i = 0; i <= 4; i++) {
                double t = i / 4.0;
                level.sendParticles(particle, a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t,
                        a.z + (b.z - a.z) * t, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player != null) {
            LAST_QUERY.remove(player.getUUID());
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("rcv.command.clear"), false);
        return 1;
    }

    private static int refresh(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        QueryState state = LAST_QUERY.get(player.getUUID());
        if (state == null) {
            ctx.getSource().sendFailure(Component.translatable("rcv.command.empty", RcvServerConfig.get().defaultDepth));
            return 0;
        }
        return run(ctx, state.mode(), state.depth(), state.options());
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        RcvServerConfig.reload();
        ctx.getSource().sendSuccess(() -> Component.translatable("rcv.command.reload"), true);
        return 1;
    }
}

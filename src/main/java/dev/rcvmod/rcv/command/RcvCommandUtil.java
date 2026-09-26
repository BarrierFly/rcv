package dev.rcvmod.rcv.command;

import dev.rcvmod.rcv.core.ConnectionGraph;
import dev.rcvmod.rcv.core.EdgeType;
import dev.rcvmod.rcv.core.GraphNode;
import dev.rcvmod.rcv.core.Region;
import dev.rcvmod.rcv.core.TypeMask;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Shared parsing/formatting for the server and client {@code /rcv} commands. */
public final class RcvCommandUtil {

    public static final int MAX_EDGE_LINES = 24;

    private RcvCommandUtil() {
    }

    public record Parsed(int depth, TypeMask mask, Region region) {
    }

    /**
     * Parses the trailing option string: {@code --no-<type>} flags and
     * {@code --region <x1> <y1> <z1> <x2> <y2> <z2>} (absolute integers).
     */
    public static Parsed parse(String raw, int defaultDepth, TypeMask baseMask) {
        int depth = defaultDepth;
        TypeMask mask = baseMask.copy();
        Region region = null;
        if (raw != null && !raw.isBlank()) {
            String[] tokens = raw.trim().split("\\s+");
            for (int i = 0; i < tokens.length; i++) {
                String token = tokens[i];
                if (token.equalsIgnoreCase("--region") && i + 6 < tokens.length) {
                    try {
                        BlockPos a = new BlockPos(Integer.parseInt(tokens[i + 1]), Integer.parseInt(tokens[i + 2]),
                                Integer.parseInt(tokens[i + 3]));
                        BlockPos b = new BlockPos(Integer.parseInt(tokens[i + 4]), Integer.parseInt(tokens[i + 5]),
                                Integer.parseInt(tokens[i + 6]));
                        region = Region.of(a, b);
                        i += 6;
                    } catch (NumberFormatException ignored) {
                        // leave region null
                    }
                } else if (token.startsWith("--no-")) {
                    EdgeType type = EdgeType.byId(token.substring("--no-".length()));
                    if (type != null) {
                        mask.set(type, false);
                    }
                } else if (token.startsWith("--depth=")) {
                    try {
                        depth = Integer.parseInt(token.substring("--depth=".length()));
                    } catch (NumberFormatException ignored) {
                        // keep default
                    }
                }
            }
        }
        return new Parsed(depth, mask, region);
    }

    public static Component typeList() {
        StringBuilder builder = new StringBuilder();
        for (EdgeType type : EdgeType.values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(type.id());
        }
        return Component.literal(builder.toString());
    }

    public static List<Component> edgeLines(ConnectionGraph graph) {
        List<Component> lines = new ArrayList<>();
        int count = 0;
        for (var edge : graph.edges()) {
            if (count++ >= MAX_EDGE_LINES) {
                lines.add(Component.literal("  ... " + (graph.edgeCount() - MAX_EDGE_LINES) + " more"));
                break;
            }
            GraphNode from = graph.node(edge.from);
            GraphNode to = graph.node(edge.to);
            StringBuilder sb = new StringBuilder("  ");
            sb.append(edge.type.id()).append(": ")
                    .append(from == null ? "?" : from.pos.toShortString())
                    .append(edge.directed ? " -> " : " <-> ")
                    .append(to == null ? "?" : to.pos.toShortString());
            if (edge.hasVia()) {
                sb.append(" via ").append(edge.via());
            }
            if (edge.dir != null) {
                sb.append(" [").append(edge.dir.getName()).append(']');
            }
            lines.add(Component.literal(sb.toString()));
        }
        return lines;
    }
}

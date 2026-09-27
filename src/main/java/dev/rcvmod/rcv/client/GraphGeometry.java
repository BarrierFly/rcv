package dev.rcvmod.rcv.client;

import dev.rcvmod.rcv.config.RcvConfig;
import dev.rcvmod.rcv.core.ConnectionGraph;
import dev.rcvmod.rcv.core.GraphEdge;
import dev.rcvmod.rcv.core.GraphNode;
import dev.rcvmod.rcv.core.NodeKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Builds a renderer-agnostic list of coloured world-space line segments for the current graph.
 * Version-specific {@code RenderCompat} implementations only have to emit these segments (§8.5).
 */
public final class GraphGeometry {

    private static final int COMPONENT_COLOR = 0xFFE8E8E8;
    private static final int CONDUCTOR_COLOR = 0xFF8A8A8A;
    private static final int MOVED_COLOR = 0xFFFFAA33;
    private static final int VIA_COLOR = 0xFFFF66FF;
    private static final int ORIGIN_COLOR = 0xFFFFFFFF;

    private GraphGeometry() {
    }

    public record Segment(Vec3 a, Vec3 b, int color, float width) {
    }

    public static List<Segment> build(ConnectionGraph graph, RcvConfig config) {
        List<Segment> segments = new ArrayList<>();
        for (GraphEdge edge : graph.edges()) {
            appendEdge(segments, graph, edge, config);
        }
        for (GraphNode node : graph.nodes()) {
            appendNode(segments, node, config);
        }
        return segments;
    }

    /**
     * Expands a segment into a camera-facing ribbon so lines can be drawn as quads and their width
     * does not depend on the GL line renderer (which fixes the width to ~2.5px on 1.21.1/1.21.10).
     *
     * <p>Returns the four corners relative to {@code camera} in triangle-strip order
     * {@code (a-, a+, b-, b+)}. {@code tanHalfFovOverHeight} converts one pixel at distance 1 to
     * world units, so the on-screen thickness stays constant regardless of distance.
     */
    public static List<Vec3> ribbon(Segment s, Vec3 camera, double tanHalfFovOverHeight) {
        Vec3 a = s.a().subtract(camera);
        Vec3 b = s.b().subtract(camera);
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1.0E-9) {
            return List.of();
        }
        dir = dir.normalize();
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 toCamera = mid.scale(-1.0);
        Vec3 side = dir.cross(toCamera);
        if (side.lengthSqr() < 1.0E-9) {
            Vec3 reference = Math.abs(dir.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
            side = dir.cross(reference);
        }
        double halfWidth = s.width() * mid.length() * tanHalfFovOverHeight;
        side = side.normalize().scale(halfWidth);
        return List.of(a.subtract(side), a.add(side), b.subtract(side), b.add(side));
    }

    private static void appendNode(List<Segment> segments, GraphNode node, RcvConfig config) {
        double cx = node.pos.getX() + 0.5;
        double cy = node.pos.getY() + 0.5;
        double cz = node.pos.getZ() + 0.5;
        int color;
        double half;
        if (node.origin) {
            color = ORIGIN_COLOR;
            half = 0.42;
        } else if (node.kind == NodeKind.CONDUCTOR) {
            color = CONDUCTOR_COLOR;
            half = 0.16;
        } else if (node.kind == NodeKind.MOVED) {
            color = MOVED_COLOR;
            half = 0.3;
        } else if (node.kind == NodeKind.VIA) {
            color = VIA_COLOR;
            half = 0.22;
        } else {
            color = COMPONENT_COLOR;
            half = 0.28;
        }
        double[][] corners = {
                {cx - half, cy - half, cz - half}, {cx + half, cy - half, cz - half},
                {cx + half, cy - half, cz + half}, {cx - half, cy - half, cz + half},
                {cx - half, cy + half, cz - half}, {cx + half, cy + half, cz - half},
                {cx + half, cy + half, cz + half}, {cx - half, cy + half, cz + half}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            segments.add(new Segment(vec(corners[e[0]]), vec(corners[e[1]]), color, config.lineWidth));
        }
    }

    private static void appendEdge(List<Segment> segments, ConnectionGraph graph, GraphEdge edge, RcvConfig config) {
        GraphNode from = graph.node(edge.from);
        GraphNode to = graph.node(edge.to);
        if (from == null || to == null) {
            return;
        }
        int color = withAlpha(config.color(edge.type), alphaForDepth(from.depth));
        float width = config.lineWidth;
        Vec3 a = Vec3.atCenterOf(from.pos);
        if (edge.hasVia()) {
            Vec3 previous = a;
            for (BlockPos via : edge.via()) {
                Vec3 next = Vec3.atCenterOf(via);
                segments.add(new Segment(previous, next, color, width));
                previous = next;
            }
            segments.add(new Segment(previous, Vec3.atCenterOf(to.pos), color, width));
        } else {
            segments.add(new Segment(a, Vec3.atCenterOf(to.pos), color, width));
        }
        if (edge.directed) {
            appendArrow(segments, a, Vec3.atCenterOf(to.pos), color, width);
        }
    }

    private static void appendArrow(List<Segment> segments, Vec3 from, Vec3 to, int color, float width) {
        Vec3 direction = to.subtract(from);
        if (direction.lengthSqr() < 1.0E-6) {
            return;
        }
        Vec3 unit = direction.normalize();
        Vec3 perpendicular = unit.cross(new Vec3(0, 1, 0));
        if (perpendicular.lengthSqr() < 1.0E-4) {
            perpendicular = unit.cross(new Vec3(1, 0, 0));
        }
        perpendicular = perpendicular.normalize().scale(0.22);
        Vec3 tip = to.subtract(unit.scale(0.35));
        segments.add(new Segment(tip.add(perpendicular), to, color, width));
        segments.add(new Segment(tip.subtract(perpendicular), to, color, width));
    }

    private static Vec3 vec(double[] xyz) {
        return new Vec3(xyz[0], xyz[1], xyz[2]);
    }

    private static float alphaForDepth(int depth) {
        return Math.max(0.25f, 1.0f - depth * 0.06f);
    }

    private static int withAlpha(int color, float factor) {
        int alpha = (int) (((color >>> 24) & 0xFF) * factor);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}

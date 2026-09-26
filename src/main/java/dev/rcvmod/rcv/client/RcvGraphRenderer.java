package dev.rcvmod.rcv.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rcvmod.rcv.config.RcvConfig;
import dev.rcvmod.rcv.core.ConnectionGraph;
import dev.rcvmod.rcv.core.GraphEdge;
import dev.rcvmod.rcv.core.GraphNode;
import dev.rcvmod.rcv.core.NodeKind;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** World-space wireframe overlay for the current graph (§8.1). */
public final class RcvGraphRenderer {

    private static final int COMPONENT_COLOR = 0xFFE8E8E8;
    private static final int CONDUCTOR_COLOR = 0xFF8A8A8A;
    private static final int MOVED_COLOR = 0xFFFFAA33;
    private static final int VIA_COLOR = 0xFFFF66FF;
    private static final int ORIGIN_COLOR = 0xFFFFFFFF;

    private RcvGraphRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(RcvGraphRenderer::collect);
    }

    private static void collect(WorldRenderContext context) {
        ConnectionGraph graph = RcvClientState.graph();
        if (graph == null || graph.nodeCount() == 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        PoseStack poseStack = context.matrices();
        MultiBufferSource consumers = context.consumers();
        if (poseStack == null || consumers == null) {
            return;
        }
        RcvConfig config = RcvConfig.get();
        VertexConsumer consumer = consumers.getBuffer(RenderTypes.linesTranslucent());
        PoseStack.Pose pose = poseStack.last();

        for (GraphEdge edge : graph.edges()) {
            drawEdge(consumer, pose, camera, graph, edge, config);
        }
        for (GraphNode node : graph.nodes()) {
            drawNode(consumer, pose, camera, node, config);
        }
    }

    private static void drawNode(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, GraphNode node,
                                 RcvConfig config) {
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
        box(consumer, pose, camera, cx - half, cy - half, cz - half, cx + half, cy + half, cz + half, color,
                config.lineWidth);
    }

    private static void drawEdge(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, ConnectionGraph graph,
                                 GraphEdge edge, RcvConfig config) {
        GraphNode from = graph.node(edge.from);
        GraphNode to = graph.node(edge.to);
        if (from == null || to == null) {
            return;
        }
        int base = config.color(edge.type);
        int color = withAlpha(base, alphaForDepth(from.depth));
        float width = config.lineWidth;

        Vec3 a = Vec3.atCenterOf(from.pos);
        if (edge.hasVia()) {
            Vec3 previous = a;
            for (BlockPos via : edge.via()) {
                Vec3 next = Vec3.atCenterOf(via);
                line(consumer, pose, camera, previous, next, color, width);
                previous = next;
            }
            line(consumer, pose, camera, previous, Vec3.atCenterOf(to.pos), color, width);
        } else {
            line(consumer, pose, camera, a, Vec3.atCenterOf(to.pos), color, width);
        }

        if (edge.directed) {
            arrow(consumer, pose, camera, Vec3.atCenterOf(from.pos), Vec3.atCenterOf(to.pos), color, width);
        }
    }

    private static float alphaForDepth(int depth) {
        return Math.max(0.25f, 1.0f - depth * 0.06f);
    }

    private static int withAlpha(int color, float factor) {
        int alpha = (int) (((color >>> 24) & 0xFF) * factor);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static void line(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, Vec3 a, Vec3 b, int color,
                             float width) {
        float ax = (float) (a.x - camera.x);
        float ay = (float) (a.y - camera.y);
        float az = (float) (a.z - camera.z);
        float bx = (float) (b.x - camera.x);
        float by = (float) (b.y - camera.y);
        float bz = (float) (b.z - camera.z);
        float nx = bx - ax;
        float ny = by - ay;
        float nz = bz - az;
        consumer.addVertex(pose, ax, ay, az).setNormal(pose, nx, ny, nz).setColor(color).setLineWidth(width);
        consumer.addVertex(pose, bx, by, bz).setNormal(pose, nx, ny, nz).setColor(color).setLineWidth(width);
    }

    private static void arrow(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, Vec3 from, Vec3 to, int color,
                              float width) {
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
        line(consumer, pose, camera, tip.add(perpendicular), to, color, width);
        line(consumer, pose, camera, tip.subtract(perpendicular), to, color, width);
    }

    private static void box(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, double x1, double y1, double z1,
                            double x2, double y2, double z2, int color, float width) {
        List<double[]> corners = List.of(
                new double[]{x1, y1, z1}, new double[]{x2, y1, z1}, new double[]{x2, y1, z2}, new double[]{x1, y1, z2},
                new double[]{x1, y2, z1}, new double[]{x2, y2, z1}, new double[]{x2, y2, z2}, new double[]{x1, y2, z2});
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            double[] a = corners.get(e[0]);
            double[] b = corners.get(e[1]);
            line(consumer, pose, camera, new Vec3(a[0], a[1], a[2]), new Vec3(b[0], b[1], b[2]), color, width);
        }
    }
}

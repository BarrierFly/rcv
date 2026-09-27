package dev.rcvmod.rcv.version;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rcvmod.rcv.client.GraphGeometry;
import dev.rcvmod.rcv.client.RcvClientState;
import dev.rcvmod.rcv.config.RcvConfig;
import dev.rcvmod.rcv.core.ConnectionGraph;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

public final class RenderCompat {

    private RenderCompat() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(RenderCompat::render);
    }

    private static void render(WorldRenderContext context) {
        ConnectionGraph graph = RcvClientState.graph();
        if (graph == null || graph.nodeCount() == 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        MultiBufferSource consumers = context.consumers();
        PoseStack poseStack = context.matrices();
        if (consumers == null || poseStack == null) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        VertexConsumer consumer = consumers.getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();
        for (GraphGeometry.Segment segment : GraphGeometry.build(graph, RcvConfig.get())) {
            emit(consumer, pose, camera, segment);
        }
    }

    private static void emit(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, GraphGeometry.Segment s) {
        float ax = (float) (s.a().x - camera.x);
        float ay = (float) (s.a().y - camera.y);
        float az = (float) (s.a().z - camera.z);
        float bx = (float) (s.b().x - camera.x);
        float by = (float) (s.b().y - camera.y);
        float bz = (float) (s.b().z - camera.z);
        float nx = bx - ax;
        float ny = by - ay;
        float nz = bz - az;
        RenderSystem.lineWidth(s.width());
        consumer.addVertex(pose, ax, ay, az).setNormal(pose, nx, ny, nz).setColor(s.color());
        consumer.addVertex(pose, bx, by, bz).setNormal(pose, nx, ny, nz).setColor(s.color());
    }
}

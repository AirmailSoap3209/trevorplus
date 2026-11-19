package com.trevorplus.client;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.trevorplus.Trevorplus;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Theodolite blocks as real 3D boxes in the world, through walls, in one batch per frame.
 * Built the same way SkyHanni does it on 26.2: a see-through variant of the vanilla "debug filled"
 * pipeline (depth test removed), submitted from the level-render COLLECT_SUBMITS event with
 * coordinates relative to the camera. If anything about this fails, it switches itself off and the
 * (much cheaper, top-faces-only) screen overlay takes over.
 */
final class BlockXray {
    private static RenderType type;
    private static boolean failed;

    private BlockXray() {}

    static boolean active() { return type != null && !failed; }

    /** Call once at mod start-up, before the first frame. */
    static void init() {
        try {
            RenderPipeline pipeline = RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                            .withLocation(Trevorplus.id("block_xray"))
                            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                            .withPrimitiveTopology(PrimitiveTopology.QUADS)
                            .withCull(false)
                            .withDepthStencilState(Optional.empty())      // no depth test = visible through walls
                            .build());
            type = RenderType.create("trevorplus_block_xray", RenderSetup.builder(pipeline).createRenderSetup());
            Trevorplus.LOGGER.info("[xray] block highlight pipeline ready");
        } catch (Throwable t) {
            failed = true;
            Trevorplus.LOGGER.warn("[xray] could not build the 3D block renderer, using the screen overlay: {}", t.toString());
        }
    }

    /** Level-render hook. */
    static void collect(LevelRenderContext ctx) {
        if (!active() || !TrevorplusClient.overlaysOn() || !TrevorplusClient.settings().blocksEnabled) return;
        int[] blocks = BlockHighlight.blocks();
        if (blocks.length == 0) return;
        try {
            Vec3 cam = ctx.levelState().cameraRenderState.pos;
            final double cx = cam.x, cy = cam.y, cz = cam.z;
            PoseStack stack = ctx.poseStack();
            SubmitNodeCollector collector = ctx.submitNodeCollector();
            collector.submitCustomGeometry(stack, type, (pose, vc) -> {
                for (int i = 0; i + 2 < blocks.length; i += 3) {
                    box(pose, vc, blocks[i] - cx, blocks[i + 1] - 1 - cy, blocks[i + 2] - cz);
                }
            });
        } catch (Throwable t) {
            failed = true;
            Trevorplus.LOGGER.warn("[xray] 3D block rendering failed, switching to the screen overlay: {}", t.toString());
        }
    }

    // colours (r, g, b) with a stronger top face so the surface reads clearly
    private static final float R = 0.33f, G = 0.88f, B = 1.0f;
    private static final float TOP_A = 0.38f, SIDE_A = 0.22f;
    private static final float E = 0.004f;      // grow the box a hair so faces never coincide

    /** One block whose minimum corner is (x, y, z) relative to the camera. */
    private static void box(PoseStack.Pose pose, VertexConsumer vc, double dx, double dy, double dz) {
        float x0 = (float) dx - E, y0 = (float) dy - E, z0 = (float) dz - E;
        float x1 = (float) dx + 1 + E, y1 = (float) dy + 1 + E, z1 = (float) dz + 1 + E;
        quad(pose, vc, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, TOP_A);     // top
        quad(pose, vc, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, SIDE_A);     // bottom
        quad(pose, vc, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, SIDE_A);     // north
        quad(pose, vc, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, SIDE_A);     // south
        quad(pose, vc, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, SIDE_A);     // west
        quad(pose, vc, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, SIDE_A);     // east
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float alpha) {
        vc.addVertex(pose, ax, ay, az).setColor(R, G, B, alpha);
        vc.addVertex(pose, bx, by, bz).setColor(R, G, B, alpha);
        vc.addVertex(pose, cx, cy, cz).setColor(R, G, B, alpha);
        vc.addVertex(pose, dx, dy, dz).setColor(R, G, B, alpha);
    }
}

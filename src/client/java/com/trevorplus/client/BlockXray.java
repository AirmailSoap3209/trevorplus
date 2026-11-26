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
 * Draws the Theodolite blocks as real 3D boxes in the world, in one batch per frame. Two variants of
 * the vanilla "debug filled" pipeline: a see-through one (depth test removed) and a normal one that
 * terrain hides. The ESP mode picks: Always = see-through only, Only when visible = normal only,
 * Dim = a faint see-through pass under a full-strength normal pass. Submitted from the level-render
 * COLLECT_SUBMITS event with coordinates relative to the camera. If anything about this fails, it
 * switches itself off and the (much cheaper, top-faces-only) screen overlay takes over.
 */
final class BlockXray {
    private static RenderType throughWalls, depthTested;
    private static boolean failed;
    /** Alpha multiplier for the see-through pass in Dim mode. */
    private static final float DIM_FACTOR = 0.35f;

    private BlockXray() {}

    static boolean active() { return depthTested != null && (throughWalls != null || !BuildFlags.ESP) && !failed; }

    /** Call once at mod start-up, before the first frame. */
    static void init() {
        try {
            if (BuildFlags.ESP) {          // compiled out of Trevor+ Lite
                RenderPipeline xray = RenderPipelines.register(
                        RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                                .withLocation(Trevorplus.id("block_xray"))
                                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                                .withCull(false)
                                .withDepthStencilState(Optional.empty())      // no depth test = visible through walls
                                .build());
                throughWalls = RenderType.create("trevorplus_block_xray", RenderSetup.builder(xray).createRenderSetup());
            }
            RenderPipeline normal = RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)    // keeps the snippet's depth test
                            .withLocation(Trevorplus.id("block_highlight"))
                            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                            .withPrimitiveTopology(PrimitiveTopology.QUADS)
                            .withCull(false)
                            .build());
            depthTested = RenderType.create("trevorplus_block_highlight", RenderSetup.builder(normal).createRenderSetup());
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
            if (BuildFlags.ESP) {
                switch (TrevorplusClient.settings().esp()) {
                    case ALWAYS -> submit(collector, stack, throughWalls, blocks, cx, cy, cz, 1f);
                    case VISIBLE -> submit(collector, stack, depthTested, blocks, cx, cy, cz, 1f);
                    case DIM -> {
                        submit(collector, stack, throughWalls, blocks, cx, cy, cz, DIM_FACTOR);
                        submit(collector, stack, depthTested, blocks, cx, cy, cz, 1f);
                    }
                }
            } else {
                submit(collector, stack, depthTested, blocks, cx, cy, cz, 1f);    // Lite: walls hide the boxes
            }
        } catch (Throwable t) {
            failed = true;
            Trevorplus.LOGGER.warn("[xray] 3D block rendering failed, switching to the screen overlay: {}", t.toString());
        }
    }

    private static void submit(SubmitNodeCollector collector, PoseStack stack, RenderType type, int[] blocks,
                               double cx, double cy, double cz, float alphaScale) {
        collector.submitCustomGeometry(stack, type, (pose, vc) -> {
            for (int i = 0; i + 2 < blocks.length; i += 3) {
                box(pose, vc, blocks[i] - cx, blocks[i + 1] - 1 - cy, blocks[i + 2] - cz, alphaScale);
            }
        });
    }

    // colours (r, g, b) with a stronger top face so the surface reads clearly
    private static final float R = 0.33f, G = 0.88f, B = 1.0f;
    private static final float TOP_A = 0.38f, SIDE_A = 0.22f;
    private static final float E = 0.004f;      // grow the box a hair so faces never coincide

    /** One block whose minimum corner is (x, y, z) relative to the camera. */
    private static void box(PoseStack.Pose pose, VertexConsumer vc, double dx, double dy, double dz, float k) {
        float x0 = (float) dx - E, y0 = (float) dy - E, z0 = (float) dz - E;
        float x1 = (float) dx + 1 + E, y1 = (float) dy + 1 + E, z1 = (float) dz + 1 + E;
        quad(pose, vc, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, TOP_A * k);     // top
        quad(pose, vc, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, SIDE_A * k);     // bottom
        quad(pose, vc, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, SIDE_A * k);     // north
        quad(pose, vc, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, SIDE_A * k);     // south
        quad(pose, vc, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, SIDE_A * k);     // west
        quad(pose, vc, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, SIDE_A * k);     // east
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

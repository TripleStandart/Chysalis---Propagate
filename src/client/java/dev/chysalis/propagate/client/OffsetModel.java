package dev.chysalis.propagate.client;

import dev.chysalis.propagate.Propagate;
import dev.chysalis.propagate.place.Guard;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * Wraps the terrain model of every block that could become a thing and shifts its quads by the offset.
 * Full cubes are not wrapped (they are never moved unless allow_full_blocks was on when models loaded),
 * so plain terrain keeps the fast vanilla path.
 */
public final class OffsetModel extends WrapperBlockStateModel {
    private static volatile boolean warned;

    private OffsetModel(BlockStateModel wrapped) {
        super(wrapped);
    }

    public static void register() {
        ModelLoadingPlugin.register(ctx -> ctx.modifyBlockModelAfterBake().register(ModelModifier.WRAP_PHASE,
                (model, context) -> shouldWrap(context.state()) ? new OffsetModel(model) : model));
    }

    private static boolean shouldWrap(BlockState st) {
        try {
            if (st.isAir() || st.getBlock() instanceof LiquidBlock) return false;
            if ("chysalis".equals(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace())) return false;
            if (dev.chysalis.propagate.rules.Rules.settings().allowFullBlocks) return true;
            Guard.enter();
            try {
                return !Block.isShapeFullBlock(st.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
            } finally {
                Guard.exit();
            }
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter view, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        Vec3 o = null;
        try {
            o = ClientOffsets.forModel(view, pos, state);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                Propagate.LOG.error("Propagate: could not compute the model offset at {}", pos, t);
            }
        }
        if (o == null) {
            super.emitQuads(emitter, view, pos, state, random, cullTest);
            return;
        }
        float x = (float) o.x, y = (float) o.y, z = (float) o.z;
        emitter.pushTransform(quad -> {
            quad.translate(x, y, z);
            return true;
        });
        try {
            // A moved model no longer lines up with its neighbours' faces: draw every face.
            super.emitQuads(emitter, view, pos, state, random, d -> false);
        } finally {
            emitter.popTransform();
        }
    }
}

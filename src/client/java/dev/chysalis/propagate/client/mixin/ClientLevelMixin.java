package dev.chysalis.propagate.client.mixin;

import dev.chysalis.propagate.client.ParticleShift;
import dev.chysalis.propagate.place.Placement;
import dev.chysalis.propagate.rules.Rules;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Torch flames, candle smoke, campfire sparks ... follow the moved thing; and when a support changes
 * (carved with the chisel: only its block entity changes), the things around it are re-meshed too.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "sendBlockUpdated", at = @At("TAIL"))
    private void propagate$remeshAroundSupport(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        if (Rules.isSupport(newState) || Rules.isSupport(oldState)) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.levelExtractor != null) {
                mc.levelExtractor.setBlocksDirty(pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                        pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
            }
        }
    }

    @Redirect(method = "doAnimateTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;animateTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"))
    private void propagate$animate(Block block, BlockState state, Level level, BlockPos pos, RandomSource random) {
        Vec3 o = Rules.settings().moveParticles && Rules.rule(state).moves() ? Placement.offset(level, pos, state) : null;
        ParticleShift.set(o);
        try {
            block.animateTick(state, level, pos, random);
        } finally {
            ParticleShift.set(null);
        }
    }

    // addParticle(options, x, y, z, dx, dy, dz)
    @ModifyVariable(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double propagate$x1(double v) { return v + ParticleShift.x(); }

    @ModifyVariable(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double propagate$y1(double v) { return v + ParticleShift.y(); }

    @ModifyVariable(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private double propagate$z1(double v) { return v + ParticleShift.z(); }

    // addParticle(options, force, decreased, x, y, z, dx, dy, dz)
    @ModifyVariable(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double propagate$x2(double v) { return v + ParticleShift.x(); }

    @ModifyVariable(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double propagate$y2(double v) { return v + ParticleShift.y(); }

    @ModifyVariable(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private double propagate$z2(double v) { return v + ParticleShift.z(); }

    // addAlwaysVisibleParticle(options, x, y, z, dx, dy, dz)
    @ModifyVariable(method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double propagate$x3(double v) { return v + ParticleShift.x(); }

    @ModifyVariable(method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double propagate$y3(double v) { return v + ParticleShift.y(); }

    @ModifyVariable(method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private double propagate$z3(double v) { return v + ParticleShift.z(); }

    // addAlwaysVisibleParticle(options, force, x, y, z, dx, dy, dz)
    @ModifyVariable(method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double propagate$x4(double v) { return v + ParticleShift.x(); }

    @ModifyVariable(method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double propagate$y4(double v) { return v + ParticleShift.y(); }

    @ModifyVariable(method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private double propagate$z4(double v) { return v + ParticleShift.z(); }
}

package dev.chysalis.propagate.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.chysalis.propagate.client.ClientOffsets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Beds, chests, signs, banners, heads ... (block entity renderers) are drawn at the moved position. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Unique
    private Vec3 propagate$applied;

    @Inject(method = "submit", at = @At("HEAD"))
    private void propagate$moveIn(BlockEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                  CameraRenderState camera, CallbackInfo ci) {
        Vec3 o = state == null ? null : ClientOffsets.forPos(state.blockPos);
        propagate$applied = o;
        if (o != null) pose.translate(o.x, o.y, o.z);
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void propagate$moveOut(BlockEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                   CameraRenderState camera, CallbackInfo ci) {
        Vec3 o = propagate$applied;
        propagate$applied = null;
        if (o != null) pose.translate(-o.x, -o.y, -o.z);
    }
}

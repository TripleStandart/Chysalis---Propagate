package dev.chysalis.propagate.client.mixin.compat;

import de.miraculixx.animated_doors.client.animation.AnimationInstance;
import de.miraculixx.animated_doors.client.animation.RenderInstruction;
import dev.chysalis.propagate.client.ClientOffsets;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * AnimatedDoors (Miraculixx) hides a door / trapdoor / fence gate from the chunk mesh while it swings and
 * draws the block's model itself, at the vanilla position, so Propagate's model offset was lost and things
 * on carved supports hovered mid-swing. Each render instruction's transform gets the same offset the
 * chunk model uses. Optional: @Pseudo + required=false, inactive without the mod.
 */
@Pseudo
@Mixin(targets = {
        "de.miraculixx.animated_doors.client.animation.type.DoorAnimationType",
        "de.miraculixx.animated_doors.client.animation.type.TrapdoorAnimationType",
        "de.miraculixx.animated_doors.client.animation.type.FenceGateAnimationType"
}, remap = false)
public abstract class AnimatedDoorsMixin {
    @Inject(method = "renderInstructions", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void propagate$offset(AnimationInstance instance, BlockPos pos, CallbackInfoReturnable<List<RenderInstruction>> cir) {
        List<RenderInstruction> in = cir.getReturnValue();
        if (in == null || in.isEmpty() || pos == null) return;
        Vec3 o = ClientOffsets.forPos(pos);
        if (o == null) return;
        List<RenderInstruction> out = new ArrayList<>(in.size());
        for (RenderInstruction ri : in) {
            Matrix4f t = new Matrix4f().translation((float) o.x, (float) o.y, (float) o.z).mul(ri.transform());
            out.add(new RenderInstruction(ri.state(), t, ri.filter(), ri.generatedFaces()));
        }
        cir.setReturnValue(out);
    }
}

# Round 2: bytecode of the methods Propagate hooks, to confirm assumptions.
c() { cls="$1"; m="$2"; echo "=================== javap -c $cls :: $m"; javap -c -p -cp "$CP" "$cls" 2>&1 | awk -v m="$m" 'index($0, m) && /\(/ {p=1} p {print} p && /^$/ {p=0}' | head -60; }
p() { cls="$1"; shift; echo "=================== $cls | $*"; javap -p -cp "$CP" "$cls" 2>&1 | grep -E "$*" | head -40; }
c 'net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase' 'getCollisionShape(net.minecraft.world.level.BlockGetter, net.minecraft.core.BlockPos);'
c 'net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase' 'getShape(net.minecraft.world.level.BlockGetter, net.minecraft.core.BlockPos);'
c 'net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase' 'isFaceSturdy(net.minecraft.world.level.BlockGetter, net.minecraft.core.BlockPos, net.minecraft.core.Direction);'
c net.minecraft.client.multiplayer.ClientLevel 'void doAnimateTick('
c net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher 'void submit('
c net.minecraft.world.entity.LivingEntity 'void setPosToBed(double'
p net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform 'transform|interface'
p net.minecraft.server.network.ServerGamePacketListenerImpl 'ServerPlayer|getPlayer'
p net.minecraft.client.renderer.LevelRenderer 'allChanged|void '
p net.minecraft.client.renderer.block.BlockAndTintGetter 'interface'
for j in $(echo "$CP" | tr ':' ' '); do unzip -l "$j" 2>/dev/null | awk '{print $4}' | grep -iE 'block/Red[sS]tone(Wire|Dust).*Block\.class|block/ChainBlock|block/.*Chain.*Block\.class|block/HangingRootsBlock|block/SporeBlossomBlock|block/TripWireBlock' ; done | sort -u

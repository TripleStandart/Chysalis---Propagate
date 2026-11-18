p() { cls="$1"; shift; echo "=================== $cls | $*"; javap -p -cp "$CP" "$cls" 2>&1 | grep -E "$*" | head -60; }
p net.minecraft.gametest.framework.GameTestHelper 'setBlock|getBlockState|absolutePos|getLevel|succeed|fail|assert'
p net.minecraft.world.level.ClipContext 'ClipContext\('
p net.minecraft.client.renderer.extract.LevelExtractor 'private void setBlockDirty'
javap -c -p -cp "$CP" net.minecraft.client.renderer.extract.LevelExtractor 2>&1 | awk '/private void setBlockDirty\(net.minecraft.core.BlockPos, boolean\)/{p=1} p{print} p&&/^$/{exit}' | head -40

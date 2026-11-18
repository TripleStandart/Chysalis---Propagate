p() { cls="$1"; shift; echo "=================== $cls | $*"; javap -p -cp "$CP" "$cls" 2>&1 | grep -E "$*" | head -40; }
p net.minecraft.client.renderer.LevelRenderer 'Dirty|allChanged|Changed|reload|ViewArea|SectionRenderDispatcher|extractor|Extractor'
p net.minecraft.client.renderer.extract.LevelExtractor 'Dirty|allChanged|Changed|public '
p net.minecraft.client.Minecraft 'Extractor|levelRenderer'

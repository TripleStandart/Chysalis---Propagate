package dev.chysalis.propagate.geom;

import dev.chysalis.propagate.config.Category;
import dev.chysalis.propagate.config.Settings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    @Test
    void defaultsRoundTripThroughTheFile() {
        Settings d = Settings.defaults();
        List<String> problems = new ArrayList<>();
        Settings back = Settings.parse(d.toFileText(), problems);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(d.toJson(), back.toJson());
        assertEquals(6, back.categories.size());
        assertEquals("fitted", back.fallback);
        assertEquals("standing", back.blocks.get("class:net.minecraft.world.level.block.DoorBlock"));
        assertEquals("resting", back.blocks.get("class:net.minecraft.world.level.block.BedBlock"));
    }

    @Test
    void partialFileKeepsBuiltInCategoriesAndValidates() {
        String json = """
                // comments are fine
                {
                  "max_offset": 0.5,
                  "categories": {
                    "standing": { "min_coverage": 0.9 },
                    "carpets": { "anchor": "down", "needs_support": false, "footprint": "full" },
                    "none": { }
                  },
                  "blocks": {
                    "minecraft:white_carpet": "carpets",
                    "minecraft:oak_door": "standing",
                    "minecraft:stone": "nonsense"
                  }
                }
                """;
        List<String> problems = new ArrayList<>();
        Settings s = Settings.parse(json, problems);
        assertEquals(0.5, s.maxOffset, 1e-9);
        assertEquals(0.9, s.categories.get("standing").minCoverage(), 1e-9);
        assertEquals(Category.Anchor.DOWN, s.categories.get("standing").anchor(), "untouched keys keep their value");
        Category c = s.categories.get("carpets");
        assertFalse(c.needsSupport());
        assertEquals(Category.FootprintMode.FULL, c.footprint());
        assertTrue(s.categories.containsKey("hanging"));
        assertEquals("carpets", s.blocks.get("minecraft:white_carpet"));
        assertFalse(s.blocks.containsKey("minecraft:stone"));
        assertEquals(2, problems.size(), problems.toString()); // reserved "none", unknown "nonsense"
    }

    @Test
    void brokenFileFallsBackToDefaults() {
        List<String> problems = new ArrayList<>();
        Settings s = Settings.parse("{ this is not json", problems);
        assertEquals(Settings.defaults().toJson(), s.toJson());
        assertEquals(1, problems.size());
    }

    @Test
    void withBlockPutsTheEntryFirstAndCanRemoveIt() {
        Settings s = Settings.defaults().withBlock("minecraft:oak_door", "none");
        assertEquals("minecraft:oak_door", s.blocks.keySet().iterator().next());
        assertFalse(s.withBlock("minecraft:oak_door", null).blocks.containsKey("minecraft:oak_door"));
    }
}

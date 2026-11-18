package dev.chysalis.propagate.test;

import dev.chysalis.propagate.config.Settings;
import dev.chysalis.propagate.rules.Rules;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client check with screenshots: the same things on carved surfaces (front row, bottom slabs standing in
 * for chiseled blocks) and on full blocks (back row). Front row must sit half a block lower, drawn by
 * the terrain model (door, torch, lantern) and by block entity renderers (chest, bed).
 */
public class PropagateClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Settings d = Settings.defaults();
        List<String> supports = new ArrayList<>(d.supports);
        supports.add("minecraft:smooth_stone_slab");
        Rules.apply(new Settings(true, supports, d.maxOffset, false, true, true, true, d.categories, d.blocks));

        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            TestServerContext s = sp.getServer();
            s.runCommand("gamemode creative @a");
            s.runCommand("time set 6000");
            s.runCommand("weather clear");
            s.runCommand("fill -4 99 -4 12 99 12 minecraft:stone");
            s.runCommand("fill -4 100 -4 12 106 12 minecraft:air");
            for (int x = 0; x <= 10; x++) {
                s.runCommand("setblock " + x + " 100 0 minecraft:smooth_stone_slab[type=bottom]");
                s.runCommand("setblock " + x + " 100 3 minecraft:stone");
            }
            for (int z : new int[]{0, 3}) {
                s.runCommand("setblock 0 101 " + z + " minecraft:torch");
                s.runCommand("setblock 2 101 " + z + " minecraft:oak_door[half=lower,facing=south]");
                s.runCommand("setblock 2 102 " + z + " minecraft:oak_door[half=upper,facing=south]");
                s.runCommand("setblock 4 101 " + z + " minecraft:chest[facing=south]");
                s.runCommand("setblock 6 101 " + z + " minecraft:red_bed[facing=east,part=foot]");
                s.runCommand("setblock 7 101 " + z + " minecraft:red_bed[facing=east,part=head]");
                s.runCommand("setblock 9 101 " + z + " minecraft:lantern[hanging=false]");
                s.runCommand("setblock 10 101 " + z + " minecraft:candle[candles=3,lit=true]");
            }
            // a lantern hanging into a ceiling carved from below (top slab)
            s.runCommand("setblock 12 103 0 minecraft:smooth_stone_slab[type=top]");
            s.runCommand("setblock 12 102 0 minecraft:lantern[hanging=true]");

            s.runCommand("tp @p 5 100 9 180 12");
            context.waitTicks(200);
            context.takeScreenshot("propagate_overview");

            s.runCommand("tp @p 2 100 2.2 180 25");
            context.waitTicks(60);
            context.takeScreenshot("propagate_closeup_door_torch");

            s.runCommand("tp @p 5.5 101 2.2 200 35");
            context.waitTicks(60);
            context.takeScreenshot("propagate_closeup_chest_bed");

            // side view along both rows: carved (left, north) vs full blocks (right)
            s.runCommand("fill -4 100 -1 -1 100 4 minecraft:air");
            s.runCommand("tp @p -3.5 100.4 1.5 -90 4");
            context.waitTicks(80);
            context.takeScreenshot("propagate_side_by_side");
        }
    }
}

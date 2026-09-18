package dev.worldbuilder.bridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingPlanServiceTest {
    @Test void openingOptionsRemoveOnlyTheDeclaredOpeningVolume() {
        Set<BlockPos> points = new LinkedHashSet<>();
        for (int x = 0; x <= 4; x++) for (int y = 0; y <= 3; y++) points.add(new BlockPos(x, y, 0));
        JsonObject operation = JsonParser.parseString("{\"options\":{\"openings\":[{\"from\":{\"x\":2,\"y\":1,\"z\":0},\"to\":{\"x\":2,\"y\":2,\"z\":0}}]}}").getAsJsonObject();
        Set<BlockPos> result = BuildingPlanService.withoutOpenings(points, operation);
        assertFalse(result.contains(new BlockPos(2, 1, 0)));
        assertFalse(result.contains(new BlockPos(2, 2, 0)));
        assertTrue(result.contains(new BlockPos(2, 0, 0)));
        assertTrue(result.contains(new BlockPos(1, 1, 0)));
    }
}

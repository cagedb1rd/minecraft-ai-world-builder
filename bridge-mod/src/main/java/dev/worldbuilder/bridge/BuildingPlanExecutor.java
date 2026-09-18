package dev.worldbuilder.bridge;

import com.google.gson.JsonObject;

public interface BuildingPlanExecutor {
    Validation validate(JsonObject plan);
    Estimate estimate(JsonObject plan);
    record Validation(boolean valid, java.util.List<String> errors, java.util.List<String> warnings) {}
    record Estimate(int blockChanges, JsonObject boundingBox) {}
}

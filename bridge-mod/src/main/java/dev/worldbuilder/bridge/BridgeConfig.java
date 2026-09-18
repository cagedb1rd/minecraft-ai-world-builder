package dev.worldbuilder.bridge;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;

public final class BridgeConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.IntValue PORT;
    public static final ForgeConfigSpec.IntValue MAX_BLOCKS_PER_BUILD;
    public static final ForgeConfigSpec.IntValue MAX_SCAN_RADIUS;
    public static final ForgeConfigSpec.IntValue MAX_BUILD_RADIUS;
    public static final ForgeConfigSpec.IntValue MAX_REGION_VOLUME;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ALLOWED_DIMENSIONS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PROTECTED_REGIONS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PLAYER_ROLES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REFERENCE_PLAYERS;
    public static final ForgeConfigSpec.IntValue BLOCKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue HISTORY_LIMIT;
    public static final ForgeConfigSpec.IntValue TRUSTED_MAX_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_BLOCK_ENTITY_OVERWRITE;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Bridge is active only when this Minecraft instance owns a server world.").push("bridge");
        ENABLED = b.comment("Enable the localhost MCP bridge and Forge client relay.").define("enabled", true);
        PORT = b.defineInRange("port", 8765, 1, 65535);
        MAX_BLOCKS_PER_BUILD = b.defineInRange("maxBlocksPerBuild", 10_000, 1, 1_000_000);
        MAX_SCAN_RADIUS = b.defineInRange("maxScanRadius", 64, 1, 512);
        MAX_BUILD_RADIUS = b.defineInRange("maxBuildRadius", 128, 1, 2048);
        MAX_REGION_VOLUME = b.defineInRange("maxRegionVolume", 262_144, 1, 16_777_216);
        ALLOWED_DIMENSIONS = b.defineListAllowEmpty("allowedDimensions", List.of("minecraft:overworld"), o -> o instanceof String);
        PROTECTED_REGIONS = b.comment("Protected region format: dimension|minX,minY,minZ|maxX,maxY,maxZ").defineListAllowEmpty("protectedRegions", List.of(), o -> o instanceof String);
        PLAYER_ROLES = b.comment("Minecraft player roles keyed by authenticated UUID: UUID=OWNER|TRUSTED|NONE").defineListAllowEmpty("playerRoles", List.of(), o -> o instanceof String);
        REFERENCE_PLAYERS = b.comment("Player UUID/name allowed as reference; * allows all positions to be read").defineListAllowEmpty("referencePlayers", List.of("*"), o -> o instanceof String);
        BLOCKS_PER_TICK = b.defineInRange("blocksPerTick", 512, 1, 100_000);
        HISTORY_LIMIT = b.defineInRange("historyLimit", 50, 1, 1000);
        TRUSTED_MAX_BLOCKS = b.defineInRange("trustedMaxBlocksPerBuild", 1000, 1, 100_000);
        ALLOW_BLOCK_ENTITY_OVERWRITE = b.comment("Unsafe for ordinary builds; preview first before enabling").define("allowBlockEntityOverwrite", false);
        b.pop(); SPEC = b.build();
    }
    private BridgeConfig() {}
}

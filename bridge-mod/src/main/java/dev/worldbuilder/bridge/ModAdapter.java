package dev.worldbuilder.bridge;

import net.minecraft.server.MinecraftServer;
import java.util.Set;

public interface ModAdapter {
    String modId();
    boolean supports(String installedVersion);
    Set<String> capabilities();
    default void onServerAvailable(MinecraftServer server) {}
}

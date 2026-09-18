package dev.worldbuilder.bridge;

import net.minecraftforge.fml.ModList;
import java.util.Optional;

public final class CreateIntegration {
    public record Status(boolean installed, String version, boolean advancedAdapterAvailable) {}
    public Status detect() { Optional<? extends net.minecraftforge.forgespi.language.IModInfo> info = ModList.get().getMods().stream().filter(m -> m.getModId().equals("create")).findFirst();
        return info.map(i -> new Status(true, i.getVersion().toString(), false)).orElseGet(() -> new Status(false, "", false)); }
}

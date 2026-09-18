package dev.worldbuilder.bridge;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

@Mod(AiWorldBuilderBridge.MOD_ID)
public final class AiWorldBuilderBridge {
    public static final String MOD_ID = "ai_world_builder_bridge"; public static final Logger LOGGER = LogUtils.getLogger();
    public AiWorldBuilderBridge() { ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BridgeConfig.SPEC); ForgeBridgeNetwork.register(); MinecraftForge.EVENT_BUS.register(this); }
    @SubscribeEvent public void onServerStarted(ServerStartedEvent event) { if(!BridgeConfig.ENABLED.get()){LOGGER.info("AI Bridge disabled by configuration");return;}BridgeRuntime.startHost(event.getServer());LOGGER.info("Integrated/Dedicated Server Bridge host active");CreateIntegration.Status create=new CreateIntegration().detect();if(create.installed())LOGGER.info("Create detected version={}; generic blocks enabled, advanced adapter={}",create.version(),create.advancedAdapterAvailable()); }
    @SubscribeEvent public void onServerStopping(ServerStoppingEvent event) { BridgeRuntime.stopHost(event.getServer()); }
    @SubscribeEvent public void onServerTick(TickEvent.ServerTickEvent event) { if(event.phase==TickEvent.Phase.END)BridgeRuntime.tick(); }
}

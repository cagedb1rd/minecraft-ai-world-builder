package dev.worldbuilder.bridge.client;

import dev.worldbuilder.bridge.AiWorldBuilderBridge;
import dev.worldbuilder.bridge.Authentication;
import dev.worldbuilder.bridge.BridgeConfig;
import dev.worldbuilder.bridge.BridgeRuntime;
import dev.worldbuilder.bridge.ForgeBridgeNetwork;
import dev.worldbuilder.bridge.LocalBridgeServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid=AiWorldBuilderBridge.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ClientBridgeBootstrap {
    private static LocalBridgeServer local;
    @SubscribeEvent public static void onClientSetup(FMLClientSetupEvent event){event.enqueueWork(()->{if(!BridgeConfig.ENABLED.get())return;try{ForgeBridgeNetwork.register();BridgeRuntime.setClientRelay(ClientBridgeRelay::route);String token=Authentication.loadOrCreateLocalToken();local=new LocalBridgeServer(BridgeRuntime::routeLocal,token);local.start();}catch(Exception e){AiWorldBuilderBridge.LOGGER.error("Local MCP Bridge is inactive: {}",e.getMessage());}});}
    @Mod.EventBusSubscriber(modid=AiWorldBuilderBridge.MOD_ID,bus=Mod.EventBusSubscriber.Bus.FORGE,value=Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event){ClientBridgeRelay.discover();}
        @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event){ClientBridgeRelay.clear();}
    }
    private ClientBridgeBootstrap(){}
}

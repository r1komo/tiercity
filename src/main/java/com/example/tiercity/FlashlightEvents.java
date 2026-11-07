package com.example.tiercity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 손전등을 켠 채 들고 있으면 2틱마다 앞을 비춘다. 내려놓거나, 끄거나, 나가면 빛을 지운다. */
@Mod.EventBusSubscriber(modid = TierCityMod.MODID)
public final class FlashlightEvents {
    public static final int RANGE = 20;

    private FlashlightEvents() {}

    private static boolean holding(Player p) {
        return FlashlightItem.isOn(p.getMainHandItem()) || FlashlightItem.isOn(p.getOffhandItem());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Object key = player.getUUID();
        if (!holding(player) || player.isSpectator()) {
            if (DynamicLights.has(key)) DynamicLights.clear(player.server, key);
            return;
        }
        if ((player.tickCount & 1) != 0) return;
        DynamicLights.beamFrom(player, key, RANGE);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) DynamicLights.clear(player.server, player.getUUID());
    }

    @SubscribeEvent
    public static void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) DynamicLights.clear(player.server, player.getUUID());
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        DynamicLights.clearAll(event.getServer());
    }
}

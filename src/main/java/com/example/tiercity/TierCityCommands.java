package com.example.tiercity;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** /tiercity locate temple|airbase : 가장 가까운 사원/공군 기지 좌표(관리자 권한). 확인과 모드팩 제작용. */
@Mod.EventBusSubscriber(modid = TierCityMod.MODID)
public final class TierCityCommands {
    private TierCityCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("tiercity")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("locate")
                        .then(Commands.literal("temple").executes(ctx -> locate(ctx.getSource(), Landmarks.TEMPLE)))
                        .then(Commands.literal("airbase").executes(ctx -> locate(ctx.getSource(), Landmarks.AIRBASE)))));
    }

    private static int locate(CommandSourceStack source, int type) {
        ServerLevel level = source.getLevel();
        if (!(level.getChunkSource().getGenerator() instanceof LayeredCityChunkGenerator)) {
            source.sendFailure(Component.translatable("message.tiercity.not_city"));
            return 0;
        }
        long seed = LayeredCityChunkGenerator.layoutSeed(level.getChunkSource().randomState());
        CityLayout.Params params = TierCityConfig.params();
        Vec3 pos = source.getPosition();
        int ci = Math.floorDiv((int) Math.floor(pos.x), Landmarks.CELL);
        int cj = Math.floorDiv((int) Math.floor(pos.z), Landmarks.CELL);
        Landmarks.Site best = null;
        double bestD = Double.MAX_VALUE;
        for (int r = 0; r <= 24; r++) {
            for (int i = ci - r; i <= ci + r; i++) {
                for (int j = cj - r; j <= cj + r; j++) {
                    if (Math.max(Math.abs(i - ci), Math.abs(j - cj)) != r) continue;
                    Landmarks.Site s = Landmarks.site(params, seed, 1, i, j);
                    if (s == null || s.type != type) continue;
                    double d = Math.hypot(s.cx - pos.x, s.cz - pos.z);
                    if (d < bestD) { bestD = d; best = s; }
                }
            }
            if (best != null && r * Landmarks.CELL > bestD + Landmarks.CELL) break;
        }
        String name = type == Landmarks.TEMPLE ? "tiercity.site.temple" : "tiercity.site.airbase";
        if (best == null) {
            source.sendFailure(Component.translatable("message.tiercity.site_not_found", Component.translatable(name)));
            return 0;
        }
        final Landmarks.Site found = best;
        final int dist = (int) Math.round(bestD);
        source.sendSuccess(() -> Component.translatable("message.tiercity.site_found", Component.translatable(name),
                found.cx, found.base + 1, found.cz, dist), false);
        return 1;
    }
}

package com.example.tiercity;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/tiercity-common.toml. 월드를 만들기 전에 수정할 것 (이미 생성된 청크는 바뀌지 않음). */
public final class TierCityConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue RADIUS;
    private static final ForgeConfigSpec.IntValue SPAWN_X;
    private static final ForgeConfigSpec.IntValue SPAWN_Z;
    private static final ForgeConfigSpec.IntValue FRAGMENT_SIZE;
    private static final ForgeConfigSpec.IntValue HEIGHT_RANGE;
    private static final ForgeConfigSpec.IntValue BIG_STEP;
    private static final ForgeConfigSpec.DoubleValue BIG_PROB;
    private static final ForgeConfigSpec.DoubleValue REMOVAL_BASE;
    private static final ForgeConfigSpec.DoubleValue REMOVAL_RIM;
    private static final ForgeConfigSpec.DoubleValue CRACK_HALF_MAX;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        RADIUS = b.comment("Radius of the giant disc in blocks (centered on world origin). 80000 is about 15 in-game days on foot. Use ~3000 for testing.")
                .defineInRange("radius", 80000, 1000, 29000000);
        SPAWN_X = b.comment("World spawn X. Snapped to the nearest remaining deck.")
                .defineInRange("spawnX", 300, -29000000, 29000000);
        SPAWN_Z = b.comment("World spawn Z. Snapped to the nearest remaining deck.")
                .defineInRange("spawnZ", 0, -29000000, 29000000);
        FRAGMENT_SIZE = b.comment("Average fragment size in blocks (96 = 6 chunks).")
                .defineInRange("fragmentSize", 96, 32, 512);
        HEIGHT_RANGE = b.comment("Typical height offset between fragments (+/- blocks).")
                .defineInRange("heightRange", 3, 0, 8);
        BIG_STEP = b.comment("Extra height offset of rare large steps (blocks).")
                .defineInRange("bigStep", 10, 0, 32);
        BIG_PROB = b.comment("Chance that a fragment has a rare large step.")
                .defineInRange("bigStepChance", 0.05D, 0.0D, 1.0D);
        REMOVAL_BASE = b.comment("Base chance that a fragment is missing (varies with clustering noise). Keep below ~0.3 to stay walkable.")
                .defineInRange("removalBase", 0.12D, 0.0D, 0.4D);
        REMOVAL_RIM = b.comment("Extra removal chance near the rim of the disc.")
                .defineInRange("removalRim", 0.35D, 0.0D, 0.8D);
        CRACK_HALF_MAX = b.comment("Maximum half-width of cracks between fragments (blocks).")
                .defineInRange("crackHalfMax", 2.0D, 0.5D, 6.0D);
        SPEC = b.build();
    }

    private TierCityConfig() {}

    public static CityLayout.Params params() {
        try {
            return new CityLayout.Params(RADIUS.get(), SPAWN_X.get(), SPAWN_Z.get(), FRAGMENT_SIZE.get(),
                    HEIGHT_RANGE.get(), BIG_STEP.get(), BIG_PROB.get(), REMOVAL_BASE.get(),
                    REMOVAL_RIM.get(), CRACK_HALF_MAX.get());
        } catch (IllegalStateException e) {
            return CityLayout.Params.DEFAULT;
        }
    }
}

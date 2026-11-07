package com.example.tiercity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** 손전등: 우클릭으로 켜고 끈다. 켜진 채로 손에 들면 바라보는 쪽을 비춘다(DynamicLights). */
public class FlashlightItem extends Item {
    public static final String TAG_ON = "On";

    public FlashlightItem(Properties properties) {
        super(properties);
    }

    public static boolean isOn(ItemStack stack) {
        return stack.getItem() instanceof FlashlightItem && stack.hasTag() && stack.getTag().getBoolean(TAG_ON);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean on = !isOn(stack);
        stack.getOrCreateTag().putBoolean(TAG_ON, on);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.4F, on ? 1.4F : 1.1F);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(isOn(stack) ? "item.tiercity.flashlight.on" : "item.tiercity.flashlight.off")
                .withStyle(isOn(stack) ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
    }
}

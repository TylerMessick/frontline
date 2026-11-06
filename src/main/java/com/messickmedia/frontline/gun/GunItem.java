package com.messickmedia.frontline.gun;

import com.messickmedia.frontline.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** A firearm. Rounds in the magazine are a data component; everything about firing is in {@link GunLogic}. */
public class GunItem extends Item {
    private final GunInfo gun;

    public GunItem(GunInfo gun) {
        super(new Properties().stacksTo(1));
        this.gun = gun;
    }

    public GunInfo gun() { return gun; }

    public static int rounds(ItemStack stack) { return stack.getOrDefault(ModRegistries.AMMO.get(), 0); }

    public static void setRounds(ItemStack stack, int rounds) { stack.set(ModRegistries.AMMO.get(), Math.max(0, rounds)); }

    public static GunInfo gunOf(ItemStack stack) { return stack.getItem() instanceof GunItem g ? g.gun : null; }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack s = new ItemStack(this);
        setRounds(s, gun.magSize());
        return s;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) { return true; }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) { return false; }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) { return true; }

    @Override
    public int getBarWidth(ItemStack stack) { return Math.round(13.0F * rounds(stack) / gun.magSize()); }

    @Override
    public int getBarColor(ItemStack stack) {
        float f = (float) rounds(stack) / gun.magSize();
        return Mth.hsvToRgb(f / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("gun.frontline.kind." + gun.kind().name().toLowerCase()).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gun.frontline.ammo", rounds(stack), gun.magSize(), Component.translatable("item.frontline." + gun.ammo())).withStyle(ChatFormatting.YELLOW));
        tip.add(Component.translatable("gun.frontline.stats", String.format("%.1f", gun.damage() * gun.pellets()), gun.rpm(), (int) gun.range()).withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.translatable(gun.auto() ? "gun.frontline.auto" : "gun.frontline.semi").withStyle(ChatFormatting.DARK_GRAY));
        if (flag.isAdvanced()) tip.add(Component.literal(gun.id()).withStyle(ChatFormatting.DARK_GRAY));
    }
}

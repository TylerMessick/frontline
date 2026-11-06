package com.messickmedia.frontline.item;

import com.messickmedia.frontline.entity.SoldierEntity;
import com.messickmedia.frontline.nation.Nation;
import com.messickmedia.frontline.nation.NationData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

/**
 * Officer's baton. Right-click air: every soldier of yours within 32 blocks falls in and follows you.
 * Sneak + right-click air: they hold where they stand. Right-click a block: they rally to that spot.
 */
public class CommandBatonItem extends Item {
    private static final double RANGE = 32.0D;

    public CommandBatonItem() {
        super(new Properties().stacksTo(1));
    }

    private static List<SoldierEntity> mine(ServerLevel level, Player player) {
        Nation n = NationData.get(level.getServer()).ofPlayer(player.getUUID());
        if (n == null) return List.of();
        UUID id = n.id;
        return level.getEntitiesOfClass(SoldierEntity.class, new AABB(player.blockPosition()).inflate(RANGE), s -> id.equals(s.nationId()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResultHolder.success(stack);
        List<SoldierEntity> troops = mine(sl, player);
        if (troops.isEmpty()) { player.displayClientMessage(Component.translatable("msg.frontline.no_troops").withStyle(ChatFormatting.GRAY), true); return InteractionResultHolder.consume(stack); }
        SoldierEntity.Order order = player.isShiftKeyDown() ? SoldierEntity.Order.HOLD : SoldierEntity.Order.FOLLOW;
        for (SoldierEntity s : troops) { if (order == SoldierEntity.Order.FOLLOW) s.rallyFollow(player); else s.setOrder(order, s.blockPosition()); }
        sl.playSound(null, player.blockPosition(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), SoundSource.PLAYERS, 0.6F, 1.4F);
        player.displayClientMessage(Component.translatable("msg.frontline.baton", troops.size(), Component.translatable("order.frontline." + order.name().toLowerCase())), true);
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (!(level instanceof ServerLevel sl) || player == null) return InteractionResult.SUCCESS;
        List<SoldierEntity> troops = mine(sl, player);
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        for (SoldierEntity s : troops) s.rally(at, player);
        sl.playSound(null, at, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).value(), SoundSource.PLAYERS, 0.6F, 1.2F);
        player.displayClientMessage(Component.translatable("msg.frontline.rally", troops.size(), at.getX(), at.getY(), at.getZ()), true);
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.frontline.command_baton.desc1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.frontline.command_baton.desc2").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.frontline.command_baton.desc3").withStyle(ChatFormatting.GRAY));
    }
}

package com.messickmedia.frontline.item;

import com.messickmedia.frontline.entity.VehicleEntity;
import com.messickmedia.frontline.entity.VehicleKind;
import com.messickmedia.frontline.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

/** Places a vehicle on the block you click. */
public class VehicleItem extends Item {
    private final VehicleKind kind;

    public VehicleItem(VehicleKind kind) {
        super(new Properties().stacksTo(1));
        this.kind = kind;
    }

    public VehicleKind kind() { return kind; }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        VehicleEntity v = new VehicleEntity(ModRegistries.VEHICLES.get(kind).get(), level, kind);
        v.setPos(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
        v.setYRot(player == null ? 0 : player.getYRot());
        v.setPaint(ctx.getItemInHand().getOrDefault(ModRegistries.PAINT.get(), 0));
        if (!level.noCollision(v, v.getBoundingBox().inflate(-0.1D))) {
            if (player != null) player.displayClientMessage(Component.translatable("msg.frontline.no_room").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        level.addFreshEntity(v);
        if (player == null || !player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public Component getName(ItemStack stack) {
        int paint = stack.getOrDefault(ModRegistries.PAINT.get(), 0);
        return paint == 0 ? super.getName(stack) : Component.translatable("paint.frontline." + paint, super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("vehicle.frontline." + kind.id + ".desc").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("vehicle.frontline.controls").withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.translatable("vehicle.frontline.paint_tip").withStyle(ChatFormatting.DARK_GRAY));
    }

}

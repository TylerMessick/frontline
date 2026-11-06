package com.messickmedia.frontline.item;

import com.messickmedia.frontline.Config;
import com.messickmedia.frontline.entity.SoldierEntity;
import com.messickmedia.frontline.nation.Nation;
import com.messickmedia.frontline.nation.NationData;
import com.messickmedia.frontline.nation.Nations;
import com.messickmedia.frontline.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Right-click a villager to enlist them in your nation's army. */
public class EnlistmentPapersItem extends Item {
    public EnlistmentPapersItem() {
        super(new Properties().stacksTo(16));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Villager villager) || villager.isBaby()) return InteractionResult.PASS;
        if (!(player.level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        NationData data = NationData.get(level.getServer());
        Nation nation = data.ofPlayer(player.getUUID());
        if (nation == null) {
            player.displayClientMessage(Component.translatable("msg.frontline.need_nation").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        if (Nations.soldierCount(level, nation.id) >= Config.MAX_SOLDIERS.get()) {
            player.displayClientMessage(Component.translatable("msg.frontline.army_full", Config.MAX_SOLDIERS.get()).withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        SoldierEntity soldier = villager.convertTo(ModRegistries.SOLDIER.get(), false);
        if (soldier == null) return InteractionResult.CONSUME;
        soldier.enlist(nation, player);
        nation.recruits++;
        data.setDirty();
        if (!player.getAbilities().instabuild) stack.shrink(1);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, soldier.getX(), soldier.getY() + 1.2D, soldier.getZ(), 12, 0.4D, 0.5D, 0.4D, 0.02D);
        level.playSound(null, soldier.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
        level.playSound(null, soldier.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("msg.frontline.enlisted", soldier.getName(), nation.army), false);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.frontline.enlistment_papers.desc").withStyle(ChatFormatting.GRAY));
    }
}

package com.messickmedia.frontline.item;

import com.messickmedia.frontline.nation.FlagDesign;
import com.messickmedia.frontline.nation.Nation;
import com.messickmedia.frontline.nation.NationData;
import com.messickmedia.frontline.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Shoulder patches. A Blank Patch used in the air is stamped with your nation's painted flag. A Nation Patch used in
 * the air is sewn onto the chest armor you are wearing, where it shows on your left shoulder; the patch that was there
 * comes back to you.
 */
public class PatchItem extends Item {
    private final boolean blank;

    public PatchItem(boolean blank) {
        super(new Properties().stacksTo(blank ? 64 : 16));
        this.blank = blank;
    }

    public static ItemStack stamped(String design, String nation) {
        ItemStack s = new ItemStack(ModRegistries.NATION_PATCH.get());
        s.set(ModRegistries.FLAG.get(), design);
        s.set(ModRegistries.FLAG_NATION.get(), nation);
        return s;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResultHolder.success(stack);
        if (blank) {
            Nation n = NationData.get(sl.getServer()).ofPlayer(player.getUUID());
            if (n == null) { player.displayClientMessage(Component.translatable("msg.frontline.need_nation").withStyle(ChatFormatting.RED), true); return InteractionResultHolder.fail(stack); }
            if (!player.getAbilities().instabuild) stack.shrink(1);
            ItemStack out = stamped(n.flag, n.name);
            if (!player.getInventory().add(out)) player.drop(out, false);
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
            player.displayClientMessage(Component.translatable("msg.frontline.patch_stamped", n.name), true);
            return InteractionResultHolder.consume(stack);
        }
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof ArmorItem)) {
            player.displayClientMessage(Component.translatable("msg.frontline.patch_need_armor").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        String design = stack.getOrDefault(ModRegistries.FLAG.get(), "");
        if (!FlagDesign.valid(design)) return InteractionResultHolder.fail(stack);
        String oldDesign = chest.get(ModRegistries.FLAG.get());
        String oldNation = chest.getOrDefault(ModRegistries.FLAG_NATION.get(), "");
        chest.set(ModRegistries.FLAG.get(), design);
        chest.set(ModRegistries.FLAG_NATION.get(), stack.getOrDefault(ModRegistries.FLAG_NATION.get(), ""));
        if (!player.getAbilities().instabuild) stack.shrink(1);
        if (oldDesign != null && FlagDesign.valid(oldDesign)) {
            ItemStack back = stamped(oldDesign, oldNation);
            if (!player.getInventory().add(back)) player.drop(back, false);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
        player.displayClientMessage(Component.translatable("msg.frontline.patch_sewn", chest.getHoverName()), true);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        String nation = stack.getOrDefault(ModRegistries.FLAG_NATION.get(), "");
        return blank || nation.isEmpty() ? super.getName(stack) : Component.translatable("item.frontline.nation_patch.named", nation);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(blank ? "item.frontline.blank_patch.desc" : "item.frontline.nation_patch.desc").withStyle(ChatFormatting.GRAY));
    }
}

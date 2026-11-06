package com.messickmedia.frontline.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Tactical armor. Worn on the vanilla armor layers (which sit just outside the body, so nothing clips through the
 * player or the guns), with extra protection against bullets on top of its armor points.
 */
public class TacticalArmorItem extends ArmorItem {
    private final float bulletResist;
    private final boolean nvg;

    public TacticalArmorItem(Holder<ArmorMaterial> material, Type type, float bulletResist, boolean nvg) {
        super(material, type, new Properties().durability(type.getDurability(28)));
        this.bulletResist = bulletResist;
        this.nvg = nvg;
    }

    public float bulletResist() { return bulletResist; }

    public boolean nvg() { return nvg; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("armor.frontline.bullet", Math.round(bulletResist * 100)).withStyle(ChatFormatting.BLUE));
        if (nvg) {
            tip.add(Component.translatable("armor.frontline.nvg1").withStyle(ChatFormatting.GREEN));
            tip.add(Component.translatable("armor.frontline.nvg2").withStyle(ChatFormatting.GRAY));
        }
    }
}

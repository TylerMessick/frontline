package com.messickmedia.frontline.gun;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A box of cartridges. Reloading pulls these out of the inventory. */
public class AmmoItem extends Item {
    private final String ammoId;

    public AmmoItem(String ammoId) {
        super(new Properties().stacksTo(64));
        this.ammoId = ammoId;
    }

    public String ammoId() { return ammoId; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        StringBuilder used = new StringBuilder();
        for (GunInfo g : Guns.ALL) if (g.ammo().equals(ammoId)) {
            if (used.length() > 0) used.append(", ");
            used.append(Component.translatable("item.frontline." + g.id()).getString());
        }
        tip.add(Component.translatable("gun.frontline.used_by", used.toString()).withStyle(ChatFormatting.GRAY));
    }
}

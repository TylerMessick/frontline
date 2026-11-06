package com.messickmedia.frontline.registry;

import com.messickmedia.frontline.Frontline;
import com.messickmedia.frontline.block.CommandFlagBlock;
import com.messickmedia.frontline.block.CommandFlagBlockEntity;
import com.messickmedia.frontline.entity.Bullet;
import com.messickmedia.frontline.entity.SoldierEntity;
import com.messickmedia.frontline.gun.AmmoItem;
import com.messickmedia.frontline.gun.GunInfo;
import com.messickmedia.frontline.gun.GunItem;
import com.messickmedia.frontline.gun.Guns;
import com.messickmedia.frontline.gun.Trooper;
import com.messickmedia.frontline.item.CommandBatonItem;
import com.messickmedia.frontline.item.EnlistmentPapersItem;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModRegistries {
    private ModRegistries() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Frontline.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Frontline.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Frontline.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Frontline.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Frontline.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Frontline.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Frontline.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Frontline.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Frontline.MODID);

    public static final ResourceKey<DamageType> BULLET_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Frontline.id("bullet"));

    // ---------------------------------------------------------------- player data
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Trooper>> TROOPER =
            ATTACHMENTS.register("trooper", () -> AttachmentType.builder(() -> new Trooper()).serialize(Trooper.CODEC).copyOnDeath().build());

    // ---------------------------------------------------------------- components
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> AMMO =
            COMPONENTS.registerComponentType("ammo", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    // ---------------------------------------------------------------- particles
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MUZZLE = particle("muzzle_flash");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SMOKE = particle("gun_smoke");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BRASS = particle("brass");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPARK = particle("spark");

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> particle(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(true));
    }

    // ---------------------------------------------------------------- entities
    public static final DeferredHolder<EntityType<?>, EntityType<Bullet>> BULLET =
            ENTITIES.register("bullet", () -> EntityType.Builder.<Bullet>of(Bullet::new, MobCategory.MISC)
                    .sized(0.15F, 0.15F).clientTrackingRange(12).updateInterval(1).build("bullet"));
    public static final DeferredHolder<EntityType<?>, EntityType<SoldierEntity>> SOLDIER =
            ENTITIES.register("soldier", () -> EntityType.Builder.<SoldierEntity>of(SoldierEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10).build("soldier"));

    // ---------------------------------------------------------------- blocks
    public static final DeferredBlock<CommandFlagBlock> COMMAND_FLAG = BLOCKS.register("command_flag", () -> new CommandFlagBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0F, 6.0F).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CommandFlagBlockEntity>> FLAG_BE =
            BLOCK_ENTITIES.register("command_flag", () -> BlockEntityType.Builder.of(CommandFlagBlockEntity::new, COMMAND_FLAG.get()).build(null));

    // ---------------------------------------------------------------- items
    public static final Map<String, DeferredItem<GunItem>> GUNS = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<AmmoItem>> AMMO_ITEMS = new LinkedHashMap<>();
    static {
        for (GunInfo g : Guns.ALL) GUNS.put(g.id(), ITEMS.register(g.id(), () -> new GunItem(g)));
        for (String a : Guns.AMMO) AMMO_ITEMS.put(a, ITEMS.register(a, () -> new AmmoItem(a)));
    }
    public static final DeferredItem<Item> GUN_PARTS = ITEMS.registerSimpleItem("gun_parts");
    public static final DeferredItem<Item> ENLISTMENT_PAPERS = ITEMS.register("enlistment_papers", EnlistmentPapersItem::new);
    public static final DeferredItem<Item> COMMAND_BATON = ITEMS.register("command_baton", CommandBatonItem::new);
    public static final DeferredItem<BlockItem> COMMAND_FLAG_ITEM = ITEMS.registerSimpleBlockItem("command_flag", COMMAND_FLAG);

    // ---------------------------------------------------------------- sounds
    public static final DeferredHolder<SoundEvent, SoundEvent> S_PISTOL = sound("pistol_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> S_SMG = sound("smg_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> S_RIFLE = sound("rifle_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> S_BATTLE = sound("battle_rifle_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> S_SHOTGUN = sound("shotgun_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> S_SNIPER = sound("sniper_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> S_LMG = sound("lmg_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> S_MAG_OUT = sound("mag_out");
    public static final DeferredHolder<SoundEvent, SoundEvent> S_MAG_IN = sound("mag_in");
    public static final DeferredHolder<SoundEvent, SoundEvent> S_BOLT = sound("bolt");
    public static final DeferredHolder<SoundEvent, SoundEvent> S_DRY = sound("dry_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> S_HIT = sound("hit_marker");
    public static final DeferredHolder<SoundEvent, SoundEvent> S_KILL = sound("kill_marker");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Frontline.id(name)));
    }

    // ---------------------------------------------------------------- tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.frontline"))
            .icon(() -> GUNS.get("fl4_carbine").get().getDefaultInstance())
            .displayItems((params, out) -> {
                for (DeferredItem<GunItem> g : GUNS.values()) out.accept(g.get().getDefaultInstance());
                for (DeferredItem<AmmoItem> a : AMMO_ITEMS.values()) out.accept(a.get());
                out.accept(GUN_PARTS.get());
                out.accept(ENLISTMENT_PAPERS.get());
                out.accept(COMMAND_BATON.get());
                out.accept(COMMAND_FLAG_ITEM.get());
            }).build());

    public static void attributes(EntityAttributeCreationEvent e) {
        e.put(SOLDIER.get(), SoldierEntity.attributes().build());
    }

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
        COMPONENTS.register(bus);
        PARTICLES.register(bus);
        ENTITIES.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ITEMS.register(bus);
        SOUNDS.register(bus);
        TABS.register(bus);
    }
}

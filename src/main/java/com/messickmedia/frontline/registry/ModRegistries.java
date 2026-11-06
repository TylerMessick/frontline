package com.messickmedia.frontline.registry;

import com.messickmedia.frontline.Frontline;
import com.messickmedia.frontline.block.CommandFlagBlock;
import com.messickmedia.frontline.block.CommandFlagBlockEntity;
import com.messickmedia.frontline.entity.Bullet;
import com.messickmedia.frontline.entity.SoldierEntity;
import com.messickmedia.frontline.entity.VehicleEntity;
import com.messickmedia.frontline.entity.VehicleKind;
import com.messickmedia.frontline.item.VehicleItem;
import com.messickmedia.frontline.item.TacticalArmorItem;
import net.minecraft.Util;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.List;
import java.util.EnumMap;
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

    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, Frontline.MODID);

    private static DeferredHolder<ArmorMaterial, ArmorMaterial> material(String name) {
        return ARMOR_MATERIALS.register(name, () -> new ArmorMaterial(
                Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                    m.put(ArmorItem.Type.BOOTS, 2); m.put(ArmorItem.Type.LEGGINGS, 5); m.put(ArmorItem.Type.CHESTPLATE, 7);
                    m.put(ArmorItem.Type.HELMET, 3); m.put(ArmorItem.Type.BODY, 7);
                }),
                12, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.of(Items.IRON_INGOT),
                List.of(new ArmorMaterial.Layer(Frontline.id(name))), 1.5F, 0.05F));
    }

    /** Camo schemes shared by armor and vehicle paint: item-id prefix and armor material name. */
    public static final String[] CAMO_PREFIX = {"", "arctic_", "blackops_"};
    private static final String[] CAMO_MATERIAL = {"tactical", "arctic", "blackops"};
    public static final List<DeferredHolder<ArmorMaterial, ArmorMaterial>> ARMOR_MATS = new java.util.ArrayList<>();
    public static final List<DeferredHolder<ArmorMaterial, ArmorMaterial>> ARMOR_MATS_NVG = new java.util.ArrayList<>();
    static {
        for (String m : CAMO_MATERIAL) { ARMOR_MATS.add(material(m)); ARMOR_MATS_NVG.add(material(m + "_nvg")); }
    }

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

    public static final Map<VehicleKind, DeferredHolder<EntityType<?>, EntityType<VehicleEntity>>> VEHICLES = new EnumMap<>(VehicleKind.class);
    static {
        for (VehicleKind k : VehicleKind.values()) {
            VEHICLES.put(k, ENTITIES.register(k.id, () -> EntityType.Builder.<VehicleEntity>of((type, level) -> new VehicleEntity(type, level, k), MobCategory.MISC)
                    .sized(k.width, k.height).eyeHeight(k.eyeHeight).clientTrackingRange(12).updateInterval(2).build(k.id)));
        }
    }

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
    public static final Map<VehicleKind, DeferredItem<Item>> VEHICLE_ITEMS = new EnumMap<>(VehicleKind.class);
    static {
        for (VehicleKind k : VehicleKind.values()) VEHICLE_ITEMS.put(k, ITEMS.register(k.id, () -> new VehicleItem(k)));
    }
    /** Every armor piece in every camo, keyed by item id (combat_helmet, arctic_plate_carrier, blackops_nvg_helmet, ...). */
    public static final Map<String, DeferredItem<Item>> ARMOR = new LinkedHashMap<>();
    static {
        for (int i = 0; i < CAMO_PREFIX.length; i++) {
            final int c = i;
            String px = CAMO_PREFIX[i];
            ARMOR.put(px + "combat_helmet", ITEMS.register(px + "combat_helmet", () -> new TacticalArmorItem(ARMOR_MATS.get(c), ArmorItem.Type.HELMET, 0.06F, false)));
            ARMOR.put(px + "nvg_helmet", ITEMS.register(px + "nvg_helmet", () -> new TacticalArmorItem(ARMOR_MATS_NVG.get(c), ArmorItem.Type.HELMET, 0.06F, true)));
            ARMOR.put(px + "plate_carrier", ITEMS.register(px + "plate_carrier", () -> new TacticalArmorItem(ARMOR_MATS.get(c), ArmorItem.Type.CHESTPLATE, 0.16F, false)));
            ARMOR.put(px + "combat_pants", ITEMS.register(px + "combat_pants", () -> new TacticalArmorItem(ARMOR_MATS.get(c), ArmorItem.Type.LEGGINGS, 0.06F, false)));
            ARMOR.put(px + "combat_boots", ITEMS.register(px + "combat_boots", () -> new TacticalArmorItem(ARMOR_MATS.get(c), ArmorItem.Type.BOOTS, 0.04F, false)));
        }
    }

    /** A painted flag (FlagDesign string) on a patch or a sewn-on chest piece, and the nation it belongs to. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> FLAG =
            COMPONENTS.registerComponentType("flag", b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> FLAG_NATION =
            COMPONENTS.registerComponentType("flag_nation", b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    /** Gear handed out to a recruit by the army (swapped when the uniform changes, never dropped). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ISSUED =
            COMPONENTS.registerComponentType("issued", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    public static final DeferredItem<Item> BLANK_PATCH = ITEMS.register("blank_patch", () -> new com.messickmedia.frontline.item.PatchItem(true));
    public static final DeferredItem<Item> NATION_PATCH = ITEMS.register("nation_patch", () -> new com.messickmedia.frontline.item.PatchItem(false));

    /** Paint scheme stored on a vehicle item: 0 woodland, 1 arctic, 2 black ops. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PAINT =
            COMPONENTS.registerComponentType("paint", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
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
    public static final DeferredHolder<SoundEvent, SoundEvent> S_CANNON = sound("cannon_fire");

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
                for (DeferredItem<Item> a : ARMOR.values()) out.accept(a.get());
                out.accept(BLANK_PATCH.get());
                out.accept(ENLISTMENT_PAPERS.get());
                out.accept(COMMAND_BATON.get());
                out.accept(COMMAND_FLAG_ITEM.get());
                for (int paint = 0; paint < CAMO_PREFIX.length; paint++)
                    for (DeferredItem<Item> v : VEHICLE_ITEMS.values()) { ItemStack st = new ItemStack(v.get()); st.set(PAINT.get(), paint); out.accept(st); }
            }).build());

    public static void attributes(EntityAttributeCreationEvent e) {
        e.put(SOLDIER.get(), SoldierEntity.attributes().build());
    }

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
        ARMOR_MATERIALS.register(bus);
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

package com.messickmedia.frontline.entity;

import com.messickmedia.frontline.gun.GunInfo;
import com.messickmedia.frontline.gun.GunItem;
import com.messickmedia.frontline.gun.GunLogic;
import com.messickmedia.frontline.nation.Nation;
import com.messickmedia.frontline.nation.NationData;
import com.messickmedia.frontline.nation.Nations;
import com.messickmedia.frontline.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/** A recruited villager in uniform. Fights for a nation, takes orders from its members, and shoots whatever it is handed. */
public class SoldierEntity extends PathfinderMob {
    public enum Order { FOLLOW, HOLD, PATROL }

    private static final EntityDataAccessor<String> NATION_NAME = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RANK = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ORDER = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> AIMING = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.BOOLEAN);

    public static final String[] RANKS = {"Pvt.", "Cpl.", "Sgt.", "Lt."};
    private static final int[] RANK_KILLS = {0, 3, 8, 15};
    private static final String[] SURNAMES = {"Hale", "Brandt", "Okafor", "Reyes", "Novak", "Kessler", "Tanaka", "Lindqvist", "Marsh", "Delgado", "Ortiz",
            "Varga", "Castellano", "Bauer", "Mensah", "Whitlock", "Garza", "Ivanov", "Pruitt", "Aldana", "Fenwick", "Sorensen", "Kowalski", "Dubois", "Achebe",
            "Hargrove", "Nakamura", "Ferreira", "Quist", "Beaumont", "Lazo", "Stroud", "Vance", "Oyelaran", "Petrov", "Calloway"};

    @Nullable private UUID nationId;
    @Nullable private UUID commander;
    @Nullable private BlockPos holdPos;
    @Nullable private BlockPos homeBase;
    private String surname = "";
    private int kills;
    /** Soldier-side "magazine" so bursts pause to reload like a player would. */
    private int magLeft = -1, reloadLeft;
    private int fireCd, burstLeft, burstPause;

    public SoldierEntity(EntityType<? extends SoldierEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(NATION_NAME, "");
        b.define(COLOR, 0x6B7A3A);
        b.define(RANK, 0);
        b.define(ORDER, Order.FOLLOW.ordinal());
        b.define(AIMING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ShootGoal());
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true) {
            @Override public boolean canUse() { return !SoldierEntity.this.armed() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !SoldierEntity.this.armed() && super.canContinueToUse(); }
        });
        this.goalSelector.addGoal(3, new OrderGoal());
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(SoldierEntity.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false, e -> e instanceof Enemy && !(e instanceof net.minecraft.world.entity.monster.Creeper)));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, SoldierEntity.class, 10, true, false, e -> {
            SoldierEntity other = (SoldierEntity) e;
            if (other.nationId == null || this.nationId == null) return other.nationId != this.nationId;
            return Nations.atWar(this.level(), this.nationId, other.nationId);
        }));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, e -> {
            UUID side = Nations.sideOf(e);
            return this.nationId != null && side != null && Nations.atWar(this.level(), this.nationId, side);
        }));
    }

    // ---------------------------------------------------------------- state

    @Nullable public UUID nationId() { return nationId; }
    @Nullable public UUID commander() { return commander; }
    public String nationName() { return this.entityData.get(NATION_NAME); }
    public int color() { return this.entityData.get(COLOR); }
    public int rank() { return this.entityData.get(RANK); }
    public Order order() { return Order.values()[Math.floorMod(this.entityData.get(ORDER), Order.values().length)]; }
    public boolean aiming() { return this.entityData.get(AIMING); }
    public boolean armed() { return this.getMainHandItem().getItem() instanceof GunItem; }
    @Nullable public GunInfo gun() { return GunItem.gunOf(this.getMainHandItem()); }

    public void enlist(Nation nation, Player recruiter) {
        this.nationId = nation.id;
        this.commander = recruiter.getUUID();
        this.entityData.set(NATION_NAME, nation.name);
        this.entityData.set(COLOR, nation.color);
        if (this.surname.isEmpty()) this.surname = SURNAMES[this.random.nextInt(SURNAMES.length)];
        this.homeBase = recruiter.blockPosition();
        if (this.level() instanceof ServerLevel sl) {
            Nation.Base b = Nations.nearestBase(sl, nation, recruiter.blockPosition());
            if (b != null) this.homeBase = b.pos();
        }
        this.setOrder(Order.FOLLOW, null);
        this.refreshName();
    }

    public void refreshNationLook(Nation n) {
        this.entityData.set(NATION_NAME, n.name);
        this.entityData.set(COLOR, n.color);
        this.refreshName();
    }

    public void setOrder(Order order, @Nullable BlockPos at) {
        this.entityData.set(ORDER, order.ordinal());
        this.holdPos = order == Order.HOLD ? (at == null ? this.blockPosition() : at) : null;
        if (order == Order.PATROL && this.homeBase == null) this.homeBase = this.blockPosition();
        this.getNavigation().stop();
    }

    public void recordKill() {
        this.kills++;
        int r = 0;
        for (int i = 0; i < RANK_KILLS.length; i++) if (this.kills >= RANK_KILLS[i]) r = i;
        if (r != this.rank()) {
            this.entityData.set(RANK, r);
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(24.0D + r * 6.0D);
            this.heal(6.0F);
            this.refreshName();
            this.level().playSound(null, this.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6F, 1.2F);
        }
        if (this.level() instanceof ServerLevel sl && this.nationId != null) {
            NationData d = Nations.data(sl);
            Nation n = d == null ? null : d.byId(this.nationId);
            if (n != null) { n.kills++; d.setDirty(); }
        }
    }

    public void refreshName() {
        this.setCustomName(Component.literal(RANKS[this.rank()] + " " + this.surname).withColor(colorText()));
        this.setCustomNameVisible(true);
    }

    private int colorText() {
        int c = this.color();
        int r = Math.min(255, ((c >> 16) & 255) + 70), g = Math.min(255, ((c >> 8) & 255) + 70), b = Math.min(255, (c & 255) + 70);
        return (r << 16) | (g << 8) | b;
    }

    public String surname() { return surname; }

    // ---------------------------------------------------------------- interaction

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (this.level().isClientSide) return InteractionResult.SUCCESS;
        ItemStack held = player.getItemInHand(hand);
        UUID side = Nations.sideOf(player);
        boolean ours = this.nationId != null && this.nationId.equals(side);
        if (!ours) {
            player.displayClientMessage(Component.translatable("msg.frontline.serves", this.getName(), this.nationName()), true);
            return InteractionResult.CONSUME;
        }
        if (held.getItem() instanceof GunItem) {
            ItemStack old = this.getMainHandItem();
            this.setItemSlot(EquipmentSlot.MAINHAND, held.copy());
            this.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
            player.setItemInHand(hand, old.isEmpty() ? ItemStack.EMPTY : old);
            this.magLeft = -1;
            this.playSound(ModRegistries.S_MAG_IN.get(), 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("msg.frontline.armed", this.getName(), held.getHoverName()), true);
            return InteractionResult.CONSUME;
        }
        if (held.isEmpty()) {
            if (player.isShiftKeyDown()) {
                this.dismiss();
                player.displayClientMessage(Component.translatable("msg.frontline.dismissed", this.getName()), true);
                return InteractionResult.CONSUME;
            }
            Order next = Order.values()[(this.order().ordinal() + 1) % Order.values().length];
            this.commander = player.getUUID();
            this.setOrder(next, this.blockPosition());
            this.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8F, 1.1F);
            player.displayClientMessage(Component.translatable("msg.frontline.order", this.getName(), Component.translatable("order.frontline." + next.name().toLowerCase())), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** Sends the soldier home: back to being a villager, dropping the weapon. */
    public void dismiss() {
        if (!(this.level() instanceof ServerLevel)) return;
        if (!this.getMainHandItem().isEmpty()) this.spawnAtLocation(this.getMainHandItem());
        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        Villager v = this.convertTo(EntityType.VILLAGER, false);
        if (v != null) v.setCustomName(null);
    }

    public void rallyFollow(Player by) {
        this.commander = by.getUUID();
        this.setOrder(Order.FOLLOW, null);
    }

    public void rally(BlockPos pos, Player by) {
        this.commander = by.getUUID();
        this.setOrder(Order.HOLD, pos);
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.fireCd > 0) this.fireCd--;
        if (this.burstPause > 0) this.burstPause--;
        if (this.reloadLeft > 0 && --this.reloadLeft == 0) { GunInfo g = gun(); this.magLeft = g == null ? 0 : g.magSize(); this.playSound(ModRegistries.S_MAG_IN.get(), 0.8F, 1.0F); }
        if (!this.level().isClientSide && this.tickCount % 100 == 0 && this.nationId != null) {
            NationData d = Nations.data(this.level());
            Nation n = d == null ? null : d.byId(this.nationId);
            if (n == null) { this.nationId = null; this.entityData.set(NATION_NAME, ""); this.refreshName(); }
            else if (!n.name.equals(this.nationName()) || n.color != this.color()) this.refreshNationLook(n);
        }
    }

    /** One trigger pull at the target: the soldier version of GunLogic.fire. */
    void shootAt(LivingEntity target) {
        GunInfo gun = gun();
        if (gun == null || !(this.level() instanceof ServerLevel sl)) return;
        if (this.magLeft < 0) this.magLeft = gun.magSize();
        if (this.magLeft == 0) {
            if (this.reloadLeft == 0) { this.reloadLeft = gun.reloadTicks() + 10; this.playSound(ModRegistries.S_MAG_OUT.get(), 0.8F, 1.0F); }
            return;
        }
        if (this.fireCd > 0 || this.burstPause > 0) return;
        Vec3 from = this.getEyePosition().add(0, -0.1D, 0);
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.55D, 0);
        // lead a little
        aim = aim.add(target.getDeltaMovement().scale(2.0D));
        Vec3 dir = aim.subtract(from).normalize();
        float accuracy = Math.max(0.6F, 1.6F - this.rank() * 0.25F);
        float spread = (gun.hipSpread() + gun.adsSpread()) * 0.5F * accuracy + 0.6F;
        GunLogic.shoot(sl, this, gun, from.add(dir.scale(0.5D)), dir, spread, true);
        this.magLeft--;
        this.fireCd = Math.max(gun.fireTicks(), gun.auto() ? gun.fireTicks() : gun.fireTicks() + 4);
        if (--this.burstLeft <= 0) {
            this.burstLeft = gun.auto() ? 3 + this.random.nextInt(4) : 1;
            this.burstPause = gun.kind() == GunInfo.Kind.SNIPER ? 20 : 8 + this.random.nextInt(10);
        }
        this.swing(InteractionHand.MAIN_HAND);
    }

    // ---------------------------------------------------------------- goals

    /** Keeps a rifle's distance, aims, and fires in bursts. */
    class ShootGoal extends Goal {
        private int seeTicks, strafeTicks;
        private boolean strafeLeft;

        ShootGoal() { this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override public boolean canUse() {
            LivingEntity t = SoldierEntity.this.getTarget();
            return t != null && t.isAlive() && SoldierEntity.this.armed();
        }

        @Override public boolean canContinueToUse() { return canUse(); }

        @Override public void stop() {
            SoldierEntity.this.entityData.set(AIMING, false);
            SoldierEntity.this.getNavigation().stop();
            seeTicks = 0;
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override public void tick() {
            SoldierEntity me = SoldierEntity.this;
            LivingEntity t = me.getTarget();
            if (t == null) return;
            GunInfo gun = me.gun();
            boolean see = me.getSensing().hasLineOfSight(t);
            seeTicks = see ? Math.min(seeTicks + 1, 40) : Math.max(seeTicks - 1, -40);
            double dist = me.distanceTo(t);
            double far = gun != null && gun.kind() == GunInfo.Kind.SNIPER ? 34.0D : gun != null && gun.kind() == GunInfo.Kind.SHOTGUN ? 10.0D : 20.0D;
            double near = gun != null && gun.kind() == GunInfo.Kind.SHOTGUN ? 3.0D : 7.0D;
            me.getLookControl().setLookAt(t, 30.0F, 30.0F);
            me.entityData.set(AIMING, see && dist < far + 6);
            if (!see || dist > far) {
                me.getNavigation().moveTo(t, 1.05D);
            } else {
                me.getNavigation().stop();
                if (dist < near) me.getMoveControl().strafe(-0.5F, 0.0F);
                else {
                    if (--strafeTicks <= 0) { strafeTicks = 20 + me.random.nextInt(20); strafeLeft = me.random.nextBoolean(); }
                    me.getMoveControl().strafe(0.0F, strafeLeft ? 0.35F : -0.35F);
                }
                me.setYRot(me.getYHeadRot());
                me.yBodyRot = me.getYHeadRot();
                if (seeTicks > 4) me.shootAt(t);
            }
        }
    }

    /** Follow the commander, hold a spot, or patrol around the home base. Only runs when not fighting. */
    class OrderGoal extends Goal {
        private int cd;

        OrderGoal() { this.setFlags(EnumSet.of(Flag.MOVE)); }

        @Override public boolean canUse() { return SoldierEntity.this.getTarget() == null; }

        @Override public void tick() {
            SoldierEntity me = SoldierEntity.this;
            if (--cd > 0) return;
            cd = 10;
            switch (me.order()) {
                case FOLLOW -> {
                    Player c = me.commander == null ? null : me.level().getPlayerByUUID(me.commander);
                    if (c == null) { me.getNavigation().stop(); return; }
                    double d = me.distanceTo(c);
                    if (d > 48 && c.onGround()) { me.teleportTo(c.getX() + me.random.nextInt(5) - 2, c.getY(), c.getZ() + me.random.nextInt(5) - 2); me.getNavigation().stop(); }
                    else if (d > 5.0D) me.getNavigation().moveTo(c, d > 14 ? 1.25D : 1.0D);
                    else me.getNavigation().stop();
                }
                case HOLD -> {
                    BlockPos p = me.holdPos == null ? me.blockPosition() : me.holdPos;
                    if (me.blockPosition().distSqr(p) > 4.0D) me.getNavigation().moveTo(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D, 1.0D);
                    else me.getNavigation().stop();
                }
                case PATROL -> {
                    BlockPos home = me.homeBase == null ? me.blockPosition() : me.homeBase;
                    if (me.getNavigation().isDone() && me.random.nextInt(4) == 0) {
                        int r = 14;
                        BlockPos to = home.offset(me.random.nextInt(r * 2 + 1) - r, 0, me.random.nextInt(r * 2 + 1) - r);
                        BlockPos top = me.level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, to);
                        me.getNavigation().moveTo(top.getX() + 0.5D, top.getY(), top.getZ() + 0.5D, 0.8D);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- misc

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (this.level() instanceof ServerLevel && this.commander != null) {
            Player c = this.level().getPlayerByUUID(this.commander);
            if (c instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("msg.frontline.fallen", this.getName()).withStyle(ChatFormatting.RED), false);
        }
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    @Override
    protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.VILLAGER_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.VILLAGER_DEATH; }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, @Nullable SpawnGroupData data) {
        RandomSource r = level.getRandom();
        if (this.surname.isEmpty()) this.surname = SURNAMES[r.nextInt(SURNAMES.length)];
        this.refreshName();
        return super.finalizeSpawn(level, difficulty, type, data);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (nationId != null) tag.putUUID("Nation", nationId);
        if (commander != null) tag.putUUID("Commander", commander);
        if (holdPos != null) tag.put("Hold", NbtUtils.writeBlockPos(holdPos));
        if (homeBase != null) tag.put("Home", NbtUtils.writeBlockPos(homeBase));
        tag.putString("Surname", surname);
        tag.putString("NationName", nationName());
        tag.putInt("Color", color());
        tag.putInt("Rank", rank());
        tag.putInt("Order", order().ordinal());
        tag.putInt("Kills", kills);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        nationId = tag.hasUUID("Nation") ? tag.getUUID("Nation") : null;
        commander = tag.hasUUID("Commander") ? tag.getUUID("Commander") : null;
        holdPos = NbtUtils.readBlockPos(tag, "Hold").orElse(null);
        homeBase = NbtUtils.readBlockPos(tag, "Home").orElse(null);
        surname = tag.getString("Surname");
        this.entityData.set(NATION_NAME, tag.getString("NationName"));
        this.entityData.set(COLOR, tag.contains("Color") ? tag.getInt("Color") : 0x6B7A3A);
        this.entityData.set(RANK, tag.getInt("Rank"));
        this.entityData.set(ORDER, tag.getInt("Order"));
        kills = tag.getInt("Kills");
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(24.0D + rank() * 6.0D);
    }
}

package com.messickmedia.frontline.entity;

import com.messickmedia.frontline.Config;
import com.messickmedia.frontline.nation.Nations;
import com.messickmedia.frontline.net.ModNetwork;
import com.messickmedia.frontline.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;

/** A rifle round: fast, nearly flat, drawn as a tracer. Soldiers and players fire the same bullet. */
public class Bullet extends Projectile {
    private static final EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(Bullet.class, EntityDataSerializers.INT);

    private float damage = 5.0F;
    private int life = 60;
    private boolean fromSoldier;
    /** Explosion power on impact (0 = plain bullet). Set by vehicle cannons, bombs and rockets. */
    private float blast;

    public Bullet(EntityType<? extends Bullet> type, Level level) {
        super(type, level);
    }

    /** style 0 = normal tracer, 1 = heavy (sniper/lmg), 2 = pellet, 3 = cannon shell / rocket, 4 = bomb (falls, no tracer). */
    public static Bullet fire(ServerLevel level, @Nullable Entity owner, Vec3 from, Vec3 dir, float speed, float damage, int life, int style) {
        Bullet b = new Bullet(ModRegistries.BULLET.get(), level);
        b.setOwner(owner);
        b.damage = damage;
        b.life = life;
        b.fromSoldier = owner instanceof SoldierEntity;
        b.entityData.set(STYLE, style);
        b.setPos(from.x, from.y, from.z);
        b.setDeltaMovement(dir.normalize().scale(speed));
        ProjectileUtil.rotateTowardsMovement(b, 1.0F);
        level.addFreshEntity(b);
        return b;
    }

    public int style() { return this.entityData.get(STYLE); }

    public Bullet blast(float power) { this.blast = power; return this; }

    private void detonate(ServerLevel level, Vec3 at) {
        Entity owner = this.getOwner();
        Level.ExplosionInteraction mode = level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOB_GRIEFING) ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE;
        level.explode(owner == null ? this : owner, at.x, at.y, at.z, this.blast, mode);
        level.sendParticles(ModRegistries.SMOKE.get(), at.x, at.y + 0.5, at.z, 12, 0.6, 0.4, 0.6, 0.05);
        level.sendParticles(ModRegistries.SPARK.get(), at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STYLE, 0);
    }

    @Override
    public boolean isNoGravity() { return true; }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = this.getDeltaMovement();
        if (this.level() instanceof ServerLevel level) {
            if (--this.life <= 0) { this.discard(); return; }
            // bullets fly flat for the first second, then start to drop
            if (this.style() == 4) v = v.add(0, -0.06D, 0).scale(0.99D);
            else if (this.tickCount > 20 && this.style() != 3) v = v.add(0, -0.012D, 0);
            this.setDeltaMovement(v);
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                this.onHit(hit);
                if (this.isRemoved()) return;
            }
            if (this.isInWater()) { this.setDeltaMovement(v.scale(0.6D)); if (v.length() < 0.5D) { this.discard(); return; } }
        }
        this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
        ProjectileUtil.rotateTowardsMovement(this, 1.0F);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) return false;
        Entity owner = this.getOwner();
        if (owner != null && !Config.FRIENDLY_FIRE.get() && Nations.sameSide(owner, target)) return false;
        if (owner != null && target == owner.getVehicle()) return false;
        if (target instanceof VehicleEntity v && owner != null && v.hasPassenger(owner)) return false;
        return !(target instanceof Bullet);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(this.level() instanceof ServerLevel level)) return;
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        if (this.blast > 0) { detonate(level, result.getLocation()); }
        float dmg = this.damage * (this.fromSoldier ? Config.SOLDIER_DAMAGE.get().floatValue() : Config.GUN_DAMAGE.get().floatValue());
        boolean head = false;
        if (target instanceof LivingEntity living && Config.HEADSHOTS.get() && living.getBbHeight() > 1.0F) {
            double headLine = living.getEyeY() - 0.3D;
            head = result.getLocation().y >= headLine;
            if (head) dmg *= 2.0F;
        }
        DamageSource src = new DamageSource(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ModRegistries.BULLET_DAMAGE), this, owner);
        if (target instanceof LivingEntity living) living.invulnerableTime = 0;
        boolean hurt = target.hurt(src, dmg);
        if (hurt) {
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, result.getLocation().x, result.getLocation().y, result.getLocation().z, head ? 6 : 3, 0.1, 0.1, 0.1, 0.1);
            if (owner instanceof ServerPlayer sp) {
                boolean kill = target instanceof LivingEntity l && l.isDeadOrDying();
                PacketDistributor.sendToPlayer(sp, new ModNetwork.HitMark(kill ? 2 : head ? 1 : 0));
                var t = sp.getData(ModRegistries.TROOPER);
                t.hits++;
                if (kill) t.kills++;
            }
            if (owner instanceof SoldierEntity s && target instanceof LivingEntity l && l.isDeadOrDying()) s.recordKill();
        }
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (this.level() instanceof ServerLevel level) {
            BlockState state = level.getBlockState(result.getBlockPos());
            Vec3 at = result.getLocation();
            if (this.blast > 0) { detonate(level, at); this.discard(); return; }
            if (!state.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 6, 0.05, 0.05, 0.05, 0.15);
                level.sendParticles(ModRegistries.SPARK.get(), at.x, at.y, at.z, 3, 0.1, 0.1, 0.1, 0.2);
                level.playSound(null, BlockPos.containing(at), SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.6F, 1.6F + this.random.nextFloat() * 0.3F);
                if (state.is(net.minecraft.tags.BlockTags.IMPERMEABLE) || state.getBlock() instanceof net.minecraft.world.level.block.StainedGlassPaneBlock || state.is(net.minecraft.world.level.block.Blocks.GLASS_PANE)) {
                    level.destroyBlock(result.getBlockPos(), false, this.getOwner());
                }
            }
            this.discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", this.damage);
        tag.putInt("Life", this.life);
        tag.putInt("Style", this.style());
        tag.putFloat("Blast", this.blast);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.damage = tag.getFloat("Damage");
        this.life = tag.getInt("Life");
        this.entityData.set(STYLE, tag.getInt("Style"));
        this.blast = tag.getFloat("Blast");
    }
}

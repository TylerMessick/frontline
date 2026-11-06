package com.messickmedia.frontline.entity;

import com.messickmedia.frontline.gun.GunLogic;
import com.messickmedia.frontline.registry.ModRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * One entity class for every vehicle; {@link VehicleKind} gives it its shape and numbers.
 * Jeep and tank drive on the ground (W/S throttle, A/D steer, the tank pivots in place; its turret follows the
 * driver's look). The plane needs a run-up before it lifts and follows the pilot's look for pitch and yaw. The
 * helicopter climbs on Space, sinks when the pilot looks down, and strafes with A/D. Left mouse fires the primary
 * weapon, right mouse the secondary.
 */
public class VehicleEntity extends Entity {
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TURRET_YAW = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TURRET_PITCH = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> THROTTLE = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> HURT = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FIRED = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);

    private final VehicleKind kind;
    private float speed;
    private int primaryCd, secondaryCd;
    private boolean leftGun;
    // client interpolation
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYRot, lerpXRot;
    // client animation
    public float wheelSpin, wheelSpinO, rotor, rotorO, roll, rollO;

    public VehicleEntity(EntityType<? extends VehicleEntity> type, Level level, VehicleKind kind) {
        super(type, level);
        this.kind = kind;
        this.blocksBuilding = true;
        this.entityData.set(KIND, kind.ordinal());
        this.entityData.set(HEALTH, kind.maxHealth);
    }

    public VehicleKind kind() { return kind; }
    public float health() { return this.entityData.get(HEALTH); }
    public float turretYaw() { return this.entityData.get(TURRET_YAW); }
    public float turretPitch() { return this.entityData.get(TURRET_PITCH); }
    public float throttle() { return this.entityData.get(THROTTLE); }
    public float syncedSpeed() { return this.entityData.get(SPEED); }
    public int hurtTicks() { return this.entityData.get(HURT); }
    public int firedTicks() { return this.entityData.get(FIRED); }
    public int primaryCd() { return primaryCd; }
    public int secondaryCd() { return secondaryCd; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(KIND, 0);
        b.define(HEALTH, 100.0F);
        b.define(TURRET_YAW, 0.0F);
        b.define(TURRET_PITCH, 0.0F);
        b.define(THROTTLE, 0.0F);
        b.define(SPEED, 0.0F);
        b.define(HURT, 0);
        b.define(FIRED, 0);
    }

    // ---------------------------------------------------------------- riding

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            // sneak + use with an empty hand packs the vehicle up
            if (!this.level().isClientSide && player.getItemInHand(hand).isEmpty() && this.getPassengers().isEmpty()) {
                this.spawnAtLocation(new ItemStack(ModRegistries.VEHICLE_ITEMS.get(kind).get()));
                this.discard();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (this.level().isClientSide) return InteractionResult.SUCCESS;
        return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < kind.seats.length;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof LivingEntity l ? l : null;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction cb) {
        int i = this.getPassengers().indexOf(passenger);
        if (i < 0 || i >= kind.seats.length) i = 0;
        float[] s = kind.seats[i];
        float yaw = (float) Math.toRadians(this.getYRot());
        double x = this.getX() + (s[0] * Mth.cos(yaw) - s[2] * Mth.sin(yaw));
        double z = this.getZ() + (s[0] * Mth.sin(yaw) + s[2] * Mth.cos(yaw));
        cb.accept(passenger, x, this.getY() + s[1], z);
    }

    @Override
    public boolean isPickable() { return !this.isRemoved(); }

    @Override
    public boolean canBeCollidedWith() { return true; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    protected Entity.MovementEmission getMovementEmission() { return Entity.MovementEmission.EVENTS; }

    // ---------------------------------------------------------------- damage

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (this.level().isClientSide || this.isRemoved()) return false;
        if (src.getEntity() != null && this.hasPassenger(src.getEntity())) return false;
        if (src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) amount *= 1.5F;
        this.entityData.set(HEALTH, this.health() - amount);
        this.entityData.set(HURT, 10);
        this.markHurt();
        if (this.health() <= 0) this.destroy();
        return true;
    }

    private void destroy() {
        if (!(this.level() instanceof ServerLevel level)) return;
        for (Entity e : List.copyOf(this.getPassengers())) e.stopRiding();
        level.explode(this, this.getX(), this.getY() + 0.5D, this.getZ(), kind.blastRadius * 0.8F, Level.ExplosionInteraction.NONE);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 1, this.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ModRegistries.SMOKE.get(), this.getX(), this.getY() + 1, this.getZ(), 30, 1.0, 0.8, 1.0, 0.05);
        this.spawnAtLocation(new ItemStack(ModRegistries.GUN_PARTS.get(), 2 + this.random.nextInt(3)));
        this.discard();
    }

    @Override
    public boolean fireImmune() { return true; }

    // ---------------------------------------------------------------- ticking

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            tickLerp();
            tickAnimation();
            return;
        }
        if (this.entityData.get(HURT) > 0) this.entityData.set(HURT, this.entityData.get(HURT) - 1);
        if (this.entityData.get(FIRED) > 0) this.entityData.set(FIRED, this.entityData.get(FIRED) - 1);
        if (primaryCd > 0) primaryCd--;
        if (secondaryCd > 0) secondaryCd--;
        LivingEntity driver = this.getControllingPassenger();
        switch (kind) {
            case JEEP, TANK -> tickGround(driver);
            case PLANE -> tickPlane(driver);
            case HELI -> tickHeli(driver);
        }
        this.entityData.set(SPEED, speed);
        if (this.getY() < this.level().getMinBuildHeight() - 32) this.destroy();
        if (driver != null && kind == VehicleKind.TANK) {
            // turret tracks the driver's look, limited by the slew rate
            float want = Mth.wrapDegrees(driver.getYRot() - this.getYRot());
            float have = turretYaw();
            this.entityData.set(TURRET_YAW, have + Mth.clamp(Mth.wrapDegrees(want - have), -4.0F, 4.0F));
            float pitch = Mth.clamp(driver.getXRot(), -12.0F, 8.0F);
            this.entityData.set(TURRET_PITCH, Mth.lerp(0.3F, turretPitch(), pitch));
        } else if (driver != null && kind == VehicleKind.JEEP) {
            this.entityData.set(TURRET_YAW, Mth.wrapDegrees(driver.getYRot() - this.getYRot()));
            this.entityData.set(TURRET_PITCH, Mth.clamp(driver.getXRot(), -30.0F, 15.0F));
        }
    }

    private void tickGround(@Nullable LivingEntity driver) {
        float forward = driver == null ? 0 : driver.zza;
        float steer = driver == null ? 0 : driver.xxa;
        if (forward != 0) speed += forward * kind.accel * (forward < 0 ? 0.6F : 1.0F);
        else speed *= 0.9F;
        speed = Mth.clamp(speed, -kind.maxSpeed * 0.5F, kind.maxSpeed);
        boolean moving = Math.abs(speed) > 0.02F;
        if (steer != 0 && (moving || kind == VehicleKind.TANK)) {
            float dir = speed < -0.02F ? 1 : -1;
            float rate = kind == VehicleKind.TANK ? kind.turnRate : kind.turnRate * Mth.clamp(Math.abs(speed) / kind.maxSpeed * 2.0F, 0.3F, 1.0F);
            this.setYRot(this.getYRot() + steer * rate * dir);
        }
        this.entityData.set(THROTTLE, Math.abs(speed) / kind.maxSpeed);
        Vec3 fwd = Vec3.directionFromRotation(0, this.getYRot());
        Vec3 v = this.getDeltaMovement();
        double vy = this.onGround() ? Math.max(v.y, -0.1D) : v.y - 0.08D;
        if (this.isInWater()) { speed *= 0.8F; vy = Math.min(vy + 0.05D, 0.05D); }
        Vec3 want = new Vec3(fwd.x * speed, vy, fwd.z * speed);
        this.setDeltaMovement(want);
        this.move(MoverType.SELF, want);
        if (this.horizontalCollision && Math.abs(speed) > 0.05F) {
            // try to climb a one-block step
            AABB up = this.getBoundingBox().move(0, 1.05D, 0);
            if (this.level().noCollision(this, up)) {
                this.setPos(this.getX(), this.getY() + 1.05D, this.getZ());
                this.move(MoverType.SELF, new Vec3(fwd.x * speed, 0, fwd.z * speed));
            } else {
                speed *= 0.3F;
            }
        }
        if (driver == null && this.onGround()) speed *= 0.8F;
    }

    private void tickPlane(@Nullable LivingEntity driver) {
        float throttle = throttle();
        if (driver != null) {
            if (driver.zza > 0) throttle = Math.min(1.0F, throttle + 0.012F);
            else if (driver.zza < 0) throttle = Math.max(0.0F, throttle - 0.02F);
        } else throttle = Math.max(0.0F, throttle - 0.01F);
        this.entityData.set(THROTTLE, throttle);
        float target = throttle * kind.maxSpeed;
        speed += (target - speed) * (target > speed ? kind.accel : 0.03F);
        boolean airborne = !this.onGround();
        float lift = Mth.clamp(speed / 0.7F, 0.0F, 1.0F);
        float pitch = this.getXRot();
        float yaw = this.getYRot();
        if (driver != null) {
            if (airborne || speed > 0.45F) {
                float wantPitch = Mth.clamp(driver.getXRot(), -50.0F, 50.0F);
                pitch += Mth.clamp(wantPitch - pitch, -2.5F, 2.5F) * lift;
                float dyaw = Mth.wrapDegrees(driver.getYRot() - yaw);
                yaw += Mth.clamp(dyaw, -kind.turnRate, kind.turnRate) * Math.max(0.4F, lift);
            } else {
                pitch += (0 - pitch) * 0.2F;
                yaw += -driver.xxa * kind.turnRate * 0.6F * Mth.clamp(speed / 0.3F, 0, 1);
            }
        } else {
            pitch += (10.0F - pitch) * 0.02F;
        }
        if (!airborne && speed < 0.45F) pitch = Math.max(pitch, 0.0F) * 0.5F;
        this.setYRot(yaw);
        this.setXRot(pitch);
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
        double gravity = 0.08D * (1.0D - lift);
        Vec3 v = dir.scale(speed).add(0, -gravity, 0);
        if (!airborne && pitch >= 0) v = new Vec3(v.x, Math.min(v.y, 0), v.z);
        Vec3 before = v;
        this.setDeltaMovement(v);
        this.move(MoverType.SELF, v);
        if (this.horizontalCollision && before.horizontalDistance() > 0.5D) { this.hurt(this.damageSources().flyIntoWall(), (float) (before.horizontalDistance() * 30.0D)); speed *= 0.2F; }
        if (this.verticalCollision && before.y < -0.5D) { this.hurt(this.damageSources().fall(), (float) (-before.y * 40.0D)); speed *= 0.5F; }
        if (this.isInWater()) { this.hurt(this.damageSources().drown(), 4.0F); speed *= 0.6F; }
    }

    private void tickHeli(@Nullable LivingEntity driver) {
        float rot = throttle();
        rot = driver != null ? Math.min(1.0F, rot + 0.01F) : Math.max(0.0F, rot - 0.01F);
        this.entityData.set(THROTTLE, rot);
        Vec3 v = this.getDeltaMovement().scale(0.93D);
        if (rot >= 0.6F && driver != null) {
            float yaw = this.getYRot();
            float dyaw = Mth.wrapDegrees(driver.getYRot() - yaw);
            yaw += Mth.clamp(dyaw, -kind.turnRate, kind.turnRate);
            this.setYRot(yaw);
            Vec3 fwd = Vec3.directionFromRotation(0, yaw);
            Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
            v = v.add(fwd.scale(driver.zza * kind.accel)).add(right.scale(-driver.xxa * kind.accel * 0.7F));
            double vy = v.y;
            if (driver.jumping) vy += 0.045D;
            else if (driver.getXRot() > 25.0F) vy -= 0.03D;
            else vy += 0.006D; // hover: the rotor holds the weight, drifts up a hair so it feels lively
            vy = Mth.clamp(vy, -0.5D, 0.45D);
            if (driver.getXRot() < -25.0F) vy += 0.015D;
            v = new Vec3(v.x, vy, v.z);
            float tilt = (float) (driver.zza * 8.0F);
            this.setXRot(Mth.lerp(0.2F, this.getXRot(), tilt));
            double h = v.horizontalDistance();
            if (h > kind.maxSpeed) v = new Vec3(v.x / h * kind.maxSpeed, v.y, v.z / h * kind.maxSpeed);
        } else {
            v = v.add(0, -0.06D * (1.0F - rot * 0.9F), 0);
            this.setXRot(Mth.lerp(0.2F, this.getXRot(), 0.0F));
        }
        speed = (float) v.horizontalDistance();
        this.setDeltaMovement(v);
        Vec3 before = v;
        this.move(MoverType.SELF, v);
        if (this.horizontalCollision && before.horizontalDistance() > 0.4D) { this.hurt(this.damageSources().flyIntoWall(), (float) (before.horizontalDistance() * 25.0D)); }
        if (this.verticalCollision && before.y < -0.45D) { this.hurt(this.damageSources().fall(), (float) (-before.y * 40.0D)); }
        if (driver != null && this.onGround()) this.setDeltaMovement(this.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
    }

    // ---------------------------------------------------------------- weapons

    /** Called from the Fire packet while the player rides this vehicle. */
    public void fire(Player shooter, boolean primary, Vec3 aim) {
        if (!(this.level() instanceof ServerLevel level) || !this.hasPassenger(shooter)) return;
        if (primary ? primaryCd > 0 : secondaryCd > 0) return;
        if (!primary && !kind.hasSecondary()) return;
        if (primary) primaryCd = kind.primaryCd; else secondaryCd = kind.secondaryCd;
        this.entityData.set(FIRED, 6);
        float yawRad = (float) Math.toRadians(this.getYRot());
        Vec3 fwd = Vec3.directionFromRotation(this.getXRot(), this.getYRot());
        Vec3 right = new Vec3(Mth.cos(yawRad), 0, Mth.sin(yawRad));
        Vec3 up = new Vec3(0, 1, 0);
        switch (kind) {
            case JEEP -> {
                // ring-mounted MG at the back, aimed where the gunner looks
                Vec3 from = this.position().add(up.scale(1.95D)).add(fwd.scale(0.5D)).add(aim.scale(0.9D));
                GunLogic.shoot(level, shooter, com.messickmedia.frontline.gun.Guns.LM249, from, aim, 2.2F, false);
            }
            case TANK -> {
                float ty = (float) Math.toRadians(this.getYRot() + turretYaw());
                Vec3 gunDir = Vec3.directionFromRotation(turretPitch(), this.getYRot() + turretYaw());
                Vec3 muzzle = this.position().add(0, 1.9D, 0).add(gunDir.scale(3.9D));
                if (primary) {
                    Bullet.fire(level, shooter, muzzle, gunDir, 3.0F, 30.0F, 80, 3).blast(kind.blastRadius);
                    level.sendParticles(ModRegistries.MUZZLE.get(), muzzle.x, muzzle.y, muzzle.z, 3, 0.2, 0.2, 0.2, 0);
                    level.sendParticles(ModRegistries.SMOKE.get(), muzzle.x, muzzle.y, muzzle.z, 10, 0.3, 0.3, 0.3, 0.08);
                    level.playSound(null, muzzle.x, muzzle.y, muzzle.z, ModRegistries.S_CANNON.get(), SoundSource.PLAYERS, 4.0F, 1.0F);
                    // recoil rocks the hull
                    this.setDeltaMovement(this.getDeltaMovement().subtract(gunDir.scale(0.08D)));
                } else {
                    Vec3 coax = this.position().add(0, 1.85D, 0).add(new Vec3(-Mth.sin(ty), 0, Mth.cos(ty)).scale(1.2D)).add(new Vec3(Mth.cos(ty), 0, Mth.sin(ty)).scale(0.55D));
                    GunLogic.shoot(level, shooter, com.messickmedia.frontline.gun.Guns.LM249, coax, gunDir, 1.8F, false);
                }
            }
            case PLANE -> {
                if (primary) {
                    leftGun = !leftGun;
                    Vec3 from = this.position().add(0, 0.7D, 0).add(right.scale(leftGun ? -2.1D : 2.1D)).add(fwd.scale(0.6D));
                    Bullet.fire(level, shooter, from, fwd, 10.0F, 6.0F, 60, 1);
                    level.sendParticles(ModRegistries.MUZZLE.get(), from.x, from.y, from.z, 1, 0, 0, 0, 0);
                    level.playSound(null, from.x, from.y, from.z, ModRegistries.S_LMG.get(), SoundSource.PLAYERS, 2.0F, 1.1F);
                } else {
                    Vec3 from = this.position().add(0, 0.2D, 0).add(this.getDeltaMovement());
                    Bullet.fire(level, shooter, from, this.getDeltaMovement().add(0, -0.1D, 0).normalize(), (float) this.getDeltaMovement().length(), 10.0F, 200, 4).blast(kind.blastRadius);
                    level.playSound(null, from.x, from.y, from.z, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 1.0F, 0.7F);
                }
            }
            case HELI -> {
                if (primary) {
                    Vec3 from = this.position().add(0, 0.9D, 0).add(fwd.scale(1.9D));
                    Vec3 dir = aim.dot(fwd) > 0.4D ? aim : fwd;
                    GunLogic.shoot(level, shooter, com.messickmedia.frontline.gun.Guns.LM249, from, dir, 1.5F, false);
                } else {
                    leftGun = !leftGun;
                    Vec3 from = this.position().add(0, 1.1D, 0).add(right.scale(leftGun ? -1.1D : 1.1D)).add(fwd.scale(0.4D));
                    Bullet.fire(level, shooter, from, fwd, 2.4F, 14.0F, 70, 3).blast(kind.blastRadius * 0.6F);
                    level.sendParticles(ModRegistries.SMOKE.get(), from.x, from.y, from.z, 6, 0.1, 0.1, 0.1, 0.05);
                    level.playSound(null, from.x, from.y, from.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 2.0F, 0.6F);
                }
            }
        }
    }

    // ---------------------------------------------------------------- client

    private void tickAnimation() {
        wheelSpinO = wheelSpin;
        rotorO = rotor;
        rollO = roll;
        float s = syncedSpeed();
        wheelSpin += s * 60.0F;
        rotor += throttle() * 40.0F;
        float targetRoll = kind == VehicleKind.PLANE ? Mth.clamp(Mth.wrapDegrees(this.getYRot() - this.yRotO) * -6.0F, -45.0F, 45.0F)
                : kind == VehicleKind.HELI ? Mth.clamp(Mth.wrapDegrees(this.getYRot() - this.yRotO) * -2.0F, -15.0F, 15.0F) : 0.0F;
        roll = Mth.lerp(0.15F, roll, targetRoll);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x; this.lerpY = y; this.lerpZ = z; this.lerpYRot = yRot; this.lerpXRot = xRot;
        this.lerpSteps = 10;
    }

    private void tickLerp() {
        if (this.isControlledByLocalInstance()) { this.lerpSteps = 0; this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ()); }
        if (this.lerpSteps > 0) {
            this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot, this.lerpXRot);
            this.lerpSteps--;
        }
    }

    @Override
    public double lerpTargetX() { return this.lerpSteps > 0 ? this.lerpX : this.getX(); }
    @Override
    public double lerpTargetY() { return this.lerpSteps > 0 ? this.lerpY : this.getY(); }
    @Override
    public double lerpTargetZ() { return this.lerpSteps > 0 ? this.lerpZ : this.getZ(); }
    @Override
    public float lerpTargetXRot() { return this.lerpSteps > 0 ? (float) this.lerpXRot : this.getXRot(); }
    @Override
    public float lerpTargetYRot() { return this.lerpSteps > 0 ? (float) this.lerpYRot : this.getYRot(); }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Health")) this.entityData.set(HEALTH, tag.getFloat("Health"));
        this.entityData.set(KIND, kind.ordinal());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Health", health());
    }

    @Override
    public Component getName() {
        return Component.translatable("entity.frontline." + kind.id);
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModRegistries.VEHICLE_ITEMS.get(kind).get());
    }
}

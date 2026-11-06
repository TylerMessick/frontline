package com.messickmedia.frontline.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** One small sprite particle with its own colour, size curve, gravity and glow: muzzle flashes, smoke, brass, sparks. */
public class Mote extends TextureSheetParticle {
    private final float startSize, endSize;
    private final boolean glow;

    Mote(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites,
         float r, float g, float b, float startSize, float endSize, int life, float gravity, boolean glow, float friction) {
        super(level, x, y, z, vx, vy, vz);
        this.xd = vx; this.yd = vy; this.zd = vz;
        this.setColor(r, g, b);
        this.startSize = startSize;
        this.endSize = endSize;
        this.quadSize = startSize;
        this.lifetime = life;
        this.gravity = gravity;
        this.glow = glow;
        this.friction = friction;
        this.hasPhysics = gravity > 0;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float t = (float) this.age / this.lifetime;
        this.quadSize = startSize + (endSize - startSize) * t;
        this.alpha = glow ? 1.0F - t * t : 0.9F - t * 0.8F;
        if (this.onGround && this.gravity > 0) { this.xd *= 0.4D; this.zd *= 0.4D; }
    }

    @Override
    public int getLightColor(float pt) { return glow ? 0xF000F0 : super.getLightColor(pt); }

    @Override
    public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public enum Kind { MUZZLE, SMOKE, BRASS, SPARK }

    public record Provider(SpriteSet sprites, Kind kind) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            var r = level.random;
            return switch (kind) {
                case MUZZLE -> new Mote(level, x, y, z, 0, 0, 0, sprites, 1.0F, 0.82F, 0.45F, 0.55F, 0.15F, 2, 0.0F, true, 1.0F);
                case SMOKE -> new Mote(level, x, y, z, vx + (r.nextFloat() - 0.5F) * 0.02F, 0.02F + r.nextFloat() * 0.02F, vz + (r.nextFloat() - 0.5F) * 0.02F,
                        sprites, 0.55F, 0.55F, 0.58F, 0.12F, 0.42F, 16 + r.nextInt(8), 0.0F, false, 0.96F);
                case BRASS -> new Mote(level, x, y, z, vx + (r.nextFloat() - 0.2F) * 0.12F, 0.18F + r.nextFloat() * 0.08F, vz + (r.nextFloat() - 0.5F) * 0.08F,
                        sprites, 0.85F, 0.66F, 0.25F, 0.05F, 0.05F, 28 + r.nextInt(10), 0.9F, false, 0.98F);
                case SPARK -> new Mote(level, x, y, z, vx + (r.nextFloat() - 0.5F) * 0.3F, vy + r.nextFloat() * 0.25F, vz + (r.nextFloat() - 0.5F) * 0.3F,
                        sprites, 1.0F, 0.85F, 0.45F, 0.06F, 0.0F, 6 + r.nextInt(6), 0.6F, true, 0.9F);
            };
        }
    }
}

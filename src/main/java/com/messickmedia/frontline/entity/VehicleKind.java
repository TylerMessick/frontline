package com.messickmedia.frontline.entity;

/**
 * Stats for each vehicle. Seats are (x right, y up, z forward) in blocks from the entity origin; the first seat drives.
 *
 * @param maxSpeed     blocks per tick at full throttle
 * @param accel        speed gained per tick with the throttle held
 * @param turnRate     degrees per tick of yaw change
 * @param primaryCd    ticks between primary shots, secondaryCd for the secondary weapon
 */
public enum VehicleKind {
    JEEP("jeep", 60.0F, 0.75F, 0.03F, 4.5F, 2.0F, 1.3F, new float[][]{{-0.45F, 0.35F, -0.25F}, {0.45F, 0.35F, -0.25F}, {0.0F, 0.75F, 0.85F}}, 2, 0, 2.0F, 6.0F),
    TANK("tank", 220.0F, 0.42F, 0.012F, 2.2F, 3.2F, 2.4F, new float[][]{{0.0F, 1.55F, -0.2F}}, 60, 2, 1.6F, 3.0F),
    PLANE("plane", 90.0F, 1.7F, 0.02F, 3.0F, 2.2F, 1.6F, new float[][]{{0.0F, 0.75F, -0.3F}}, 2, 30, 1.0F, 5.0F),
    HELI("heli", 110.0F, 1.0F, 0.035F, 4.0F, 2.2F, 2.3F, new float[][]{{0.0F, 0.45F, -0.55F}, {0.0F, 0.45F, 0.2F}}, 3, 16, 1.5F, 5.0F);

    public final String id;
    public final float maxHealth, maxSpeed, accel, turnRate, width, height;
    public final float[][] seats;
    public final int primaryCd, secondaryCd;
    public final float eyeHeight, blastRadius;

    VehicleKind(String id, float maxHealth, float maxSpeed, float accel, float turnRate, float width, float height, float[][] seats, int primaryCd, int secondaryCd, float eyeHeight, float blastRadius) {
        this.id = id; this.maxHealth = maxHealth; this.maxSpeed = maxSpeed; this.accel = accel; this.turnRate = turnRate;
        this.width = width; this.height = height; this.seats = seats; this.primaryCd = primaryCd; this.secondaryCd = secondaryCd;
        this.eyeHeight = eyeHeight; this.blastRadius = blastRadius;
    }

    public boolean flies() { return this == PLANE || this == HELI; }

    public boolean hasSecondary() { return secondaryCd > 0; }
}

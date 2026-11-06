package com.messickmedia.frontline.client;

import com.messickmedia.frontline.Frontline;
import com.messickmedia.frontline.entity.SoldierEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A villager in uniform: the humanoid rig with a helmet (drawn in the hat layer), helmet rim, plate carrier with
 * pouches, belt, pack, knee pads and boots. The nation armband is a separate part the renderer tints.
 */
public class SoldierModel extends HumanoidModel<SoldierEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Frontline.id("soldier"), "main");
    public final ModelPart band;

    public SoldierModel(ModelPart root) {
        super(root);
        this.band = root.getChild("band");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.getChild("head");
        PartDefinition body = root.getChild("body");
        PartDefinition rightLeg = root.getChild("right_leg");
        PartDefinition leftLeg = root.getChild("left_leg");
        head.addOrReplaceChild("rim", CubeListBuilder.create().texOffs(64, 22).addBox(-5.0F, -5.4F, -5.0F, 10.0F, 1.0F, 10.0F), PartPose.ZERO);
        body.addOrReplaceChild("vest", CubeListBuilder.create().texOffs(64, 0).addBox(-4.5F, 0.5F, -2.5F, 9.0F, 7.5F, 5.0F), PartPose.ZERO);
        body.addOrReplaceChild("belt", CubeListBuilder.create().texOffs(64, 14).addBox(-4.4F, 10.0F, -2.4F, 8.8F, 2.0F, 4.8F), PartPose.ZERO);
        body.addOrReplaceChild("pouch_l", CubeListBuilder.create().texOffs(92, 0).addBox(-3.8F, 4.0F, -3.4F, 3.0F, 3.0F, 1.0F), PartPose.ZERO);
        body.addOrReplaceChild("pouch_r", CubeListBuilder.create().texOffs(92, 5).addBox(0.8F, 4.0F, -3.4F, 3.0F, 3.0F, 1.0F), PartPose.ZERO);
        body.addOrReplaceChild("pack", CubeListBuilder.create().texOffs(64, 34).addBox(-3.0F, 1.0F, 2.5F, 6.0F, 6.0F, 3.0F), PartPose.ZERO);
        rightLeg.addOrReplaceChild("boot", CubeListBuilder.create().texOffs(84, 34).addBox(-2.5F, 8.0F, -2.5F, 5.0F, 4.0F, 5.0F), PartPose.ZERO);
        leftLeg.addOrReplaceChild("boot", CubeListBuilder.create().texOffs(84, 34).addBox(-2.5F, 8.0F, -2.5F, 5.0F, 4.0F, 5.0F), PartPose.ZERO);
        rightLeg.addOrReplaceChild("knee", CubeListBuilder.create().texOffs(104, 22).addBox(-2.3F, 4.0F, -2.7F, 4.6F, 3.0F, 1.0F), PartPose.ZERO);
        leftLeg.addOrReplaceChild("knee", CubeListBuilder.create().texOffs(104, 22).addBox(-2.3F, 4.0F, -2.7F, 4.6F, 3.0F, 1.0F), PartPose.ZERO);
        // the armband follows the right arm; it is tinted with the nation colour by the renderer
        root.addOrReplaceChild("band", CubeListBuilder.create().texOffs(104, 28).addBox(-3.5F, -1.0F, -2.5F, 4.0F, 2.0F, 5.0F), PartPose.offset(-5.0F, 2.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(SoldierEntity e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        boolean armed = e.armed();
        this.rightArmPose = armed && e.aiming() ? ArmPose.CROSSBOW_HOLD : ArmPose.EMPTY;
        this.leftArmPose = ArmPose.EMPTY;
        super.setupAnim(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (armed && !e.aiming()) {
            // low ready: rifle across the body, both hands on it
            this.rightArm.xRot = -0.55F;
            this.rightArm.yRot = -0.15F;
            this.leftArm.xRot = -0.95F;
            this.leftArm.yRot = 0.55F;
            this.leftArm.zRot = 0.0F;
        }
        this.band.copyFrom(this.rightArm);
        // the drawn-on helmet rim would poke through a real helmet
        this.head.getChild("rim").visible = e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty();
        // the armband is drawn (tinted) only by its own layer
        this.band.visible = false;
    }
}

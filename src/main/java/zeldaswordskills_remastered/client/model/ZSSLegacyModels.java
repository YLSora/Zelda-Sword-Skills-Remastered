package zeldaswordskills_remastered.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public final class ZSSLegacyModels {
    private ZSSLegacyModels() {
    }

    public static final class PartModel<T extends LivingEntity> extends EntityModel<T> implements net.minecraft.client.model.ArmedModel {
        private final ModelPart root;
        private final ModelPart head;
        private final ModelPart rightArm;
        private final ModelPart leftArm;
        private final ModelPart rightLeg;
        private final ModelPart leftLeg;
        private final ModelPart rightWing;
        private final ModelPart leftWing;

        public PartModel(ModelPart root) {
            this.root = root;
            head = child(root, "head");
            rightArm = child(root, "right_arm");
            leftArm = child(root, "left_arm");
            rightLeg = child(root, "right_leg");
            leftLeg = child(root, "left_leg");
            rightWing = child(root, "right_wing");
            leftWing = child(root, "left_wing");
        }

        @Override
        public void translateToHand(net.minecraft.world.entity.HumanoidArm arm, PoseStack poseStack) {
            root.translateAndRotate(poseStack);
            java.util.Objects.requireNonNull(arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? rightArm : leftArm)
                    .translateAndRotate(poseStack);
        }

        @Override
        public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                              float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            if (head != null) {
                head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
                head.xRot = headPitch * Mth.DEG_TO_RAD;
            }
            if (rightArm != null) rightArm.xRot = Mth.cos(limbSwing * 0.6662F + Mth.PI) * limbSwingAmount;
            if (leftArm != null) leftArm.xRot = Mth.cos(limbSwing * 0.6662F) * limbSwingAmount;
            if (rightLeg != null) rightLeg.xRot = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
            if (leftLeg != null) leftLeg.xRot = Mth.cos(limbSwing * 0.6662F + Mth.PI) * 1.4F * limbSwingAmount;
            if (rightWing != null) rightWing.yRot = Mth.cos(ageInTicks * 0.8F) * 0.8F;
            if (leftWing != null) leftWing.yRot = -rightWing.yRot;
        }

        @Override
        public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay,
                                   float red, float green, float blue, float alpha) {
            root.render(poseStack, consumer, packedLight, packedOverlay, red, green, blue, alpha);
        }

        private static ModelPart child(ModelPart root, String name) {
            return root.hasChild(name) ? root.getChild(name) : null;
        }
    }

    public static LayerDefinition humanoidArmor() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, -8, -4, 8, 8, 8)
                .texOffs(0, 55).addBox(-4.5F, -7.6F, 3.5F, 9, 8, 1)
                .texOffs(0, 54).addBox(-4.5F, -8.6F, -4.5F, 9, 1, 9)
                .texOffs(0, 48).addBox(-4.5F, -7.6F, -4.5F, 1, 8, 8)
                .texOffs(0, 48).mirror().addBox(3.5F, -7.6F, -4.5F, 1, 8, 8)
                .texOffs(1, 62).addBox(-3.5F, -6.6F, -4.5F, 7, 1, 1)
                .texOffs(46, 11).addBox(-1, -5.6F, -4.6F, 2, 4, 1)
                .texOffs(38, 0).addBox(-10, -5.5F, -1, 5, 2, 2)
                .texOffs(38, 0).mirror().addBox(5, -5.5F, -1, 5, 2, 2), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4, 0, -2, 8, 12, 4)
                .texOffs(0, 32).addBox(-5, -0.2F, -3, 10, 7, 6)
                .texOffs(0, 46).addBox(-4.5F, 6.8F, -2.5F, 9, 6, 5)
                .texOffs(32, 45).addBox(-5, 0, 3, 10, 16, 1), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3, -2, -2, 4, 12, 4)
                .texOffs(0, 58).addBox(-5, -2.5F, -2.5F, 6, 1, 5), PartPose.offset(-5, 2, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1, -2, -2, 4, 12, 4)
                .texOffs(0, 58).mirror().addBox(-1, -2.5F, -2.5F, 6, 1, 5), PartPose.offset(5, 2, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 12, 4), PartPose.offset(-2, 12, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2, 0, -2, 4, 12, 4), PartPose.offset(2, 12, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition goron() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8),
                PartPose.offset(0, 3, 0));
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16).addBox(-6, 0, -5, 12, 12, 10)
                .texOffs(45, 38).addBox(-5, 0.5F, -5.5F, 10, 4, 1)
                .texOffs(0, 38).addBox(-5, 4.5F, -6, 10, 7, 2)
                .texOffs(65, 16).addBox(-5, 0.5F, 5, 10, 11, 1)
                .texOffs(50, 16).addBox(-4, 2, 6, 8, 8, 3), PartPose.offset(0, 3, 0));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(108, 0).addBox(-3, -2, -2, 4, 16, 5), PartPose.offset(-7, 5, -1));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(108, 21).addBox(-1, -2, -2, 4, 16, 5), PartPose.offset(7, 5, -1));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(91, 0).addBox(-2, 0, -2, 4, 9, 4), PartPose.offset(-2, 15, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(91, 13).addBox(-2, 0, -2, 4, 9, 4), PartPose.offset(2, 15, 0));
        root.addOrReplaceChild("sumo", CubeListBuilder.create().texOffs(63, 0).addBox(-3.5F, 0, -3.5F, 7, 4, 7), PartPose.offset(0, 14.5F, 1.5F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition maskTrader() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -7.5F, -5, 8, 8, 8), PartPose.offset(0, 1, -1));
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4, 0, -2, 8, 12, 4)
                .texOffs(0, 36).addBox(-6, -4.5F, 2, 12, 14, 7)
                .texOffs(0, 57).addBox(-7, -9.5F, 2, 14, 5, 6)
                .texOffs(32, 0).addBox(-10, -9.5F, 2, 6, 6, 1)
                .texOffs(46, 0).addBox(6, -1.5F, 2.5F, 1, 6, 6)
                .texOffs(60, 0).addBox(7, 5.5F, 2.5F, 1, 6, 6)
                .texOffs(32, 7).addBox(0, -1.5F, 9, 6, 6, 1)
                .texOffs(74, 0).addBox(-7, -4.5F, 2.5F, 1, 6, 6)
                .texOffs(88, 0).addBox(-8, 3.5F, 8, 6, 6, 1), PartPose.offset(0, 1.5F, -0.6F));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3, -2, -2, 4, 12, 4), PartPose.offset(-5, 4, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1, -2, -2, 4, 12, 4), PartPose.offset(5, 4, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2, 0, -2, 4, 12, 4), PartPose.offset(-2, 12, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-2, 0, -2, 4, 12, 4), PartPose.offset(2, 12, 0));
        return LayerDefinition.create(mesh, 128, 128);
    }

    public static LayerDefinition octorok() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3, 0, -1, 7, 4, 6)
                .texOffs(0, 41).addBox(-6, -1, -4, 12, 2, 12)
                .texOffs(22, 21).addBox(-5, -6, -3, 10, 5, 10)
                .texOffs(30, 0).addBox(-4, -11, -2, 8, 5, 8)
                .texOffs(88, 0).addBox(-5, -12, -3, 4, 4, 1)
                .texOffs(88, 0).addBox(1, -12, -3, 4, 4, 1)
                .texOffs(0, 31).addBox(-2.5F, -4, -6, 5, 5, 3), PartPose.offset(0, 11, 0));
        for (int i = 0; i < 6; i++) {
            double angle = i * Mth.TWO_PI / 6.0D;
            root.addOrReplaceChild("tentacle_" + i, CubeListBuilder.create().texOffs(67, 0).addBox(-1.5F, 0, -1.5F, 3, 13, 3),
                    PartPose.offsetAndRotation((float)Math.cos(angle) * 2.35F, 13, (float)Math.sin(angle) * 2.35F + 1.25F,
                            0, (float)(-angle + Math.PI / 2), 0));
        }
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition wizzrobe() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8), PartPose.ZERO);
        PartDefinition hat = head.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(24, 58).addBox(-5, -2, -5, 10, 2, 10), PartPose.offset(0, -8, 0));
        PartDefinition hat1 = hat.addOrReplaceChild("hat_1", CubeListBuilder.create().texOffs(36, 31).addBox(-3.5F, -4, -3.5F, 7, 4, 7),
                PartPose.offsetAndRotation(0, 0, 0, -0.052F, 0, 0.026F));
        PartDefinition hat2 = hat1.addOrReplaceChild("hat_2", CubeListBuilder.create().texOffs(48, 14).addBox(-2, -4, -2, 4, 4, 4),
                PartPose.offsetAndRotation(0, -4, 0, -0.105F, 0, 0.052F));
        hat2.addOrReplaceChild("hat_3", CubeListBuilder.create().texOffs(26, 18).addBox(-0.5F, -2, -0.5F, 1, 2, 1),
                PartPose.offsetAndRotation(0, -4, 0, -0.209F, 0, 0.105F));
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16).addBox(-4, 0, -2, 8, 8, 4), PartPose.ZERO);
        PartDefinition robe = body.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(32, 0).addBox(-4, 0, -3, 8, 4, 6), PartPose.offset(0, 8, 0));
        PartDefinition robeMid = robe.addOrReplaceChild("robe_mid", CubeListBuilder.create().texOffs(0, 28).addBox(-5, 0, -4, 10, 6, 8), PartPose.offset(0, 4, 0));
        robeMid.addOrReplaceChild("robe_lower", CubeListBuilder.create().texOffs(0, 42).addBox(-6, 0, -5, 12, 6, 10), PartPose.offset(0, 6, 0));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(32, 10).addBox(-3, -2, -2, 4, 12, 4), PartPose.offset(-5, 2, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(32, 10).mirror().addBox(-1, -2, -2, 4, 12, 4), PartPose.offset(5, 2, 0));
        return LayerDefinition.create(mesh, 64, 128);
    }

    public static LayerDefinition dekuBaba(boolean fire, boolean withered) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition stem1 = root.addOrReplaceChild("stem_1", CubeListBuilder.create().texOffs(56, 0)
                .addBox(-1, -11, -1, 2, 11, 2), PartPose.offset(0, 22, 0));
        PartDefinition stem2 = stem1.addOrReplaceChild("stem_2", CubeListBuilder.create().texOffs(56, 0).addBox(-1, -6, -1, 2, 6, 2), PartPose.offset(0, -11, 0));
        PartDefinition stem3 = stem2.addOrReplaceChild("stem_3", CubeListBuilder.create().texOffs(56, 0).addBox(-1, -5, -1, 2, 5, 2), PartPose.offset(0, -6, 0));
        PartDefinition head = stem3.addOrReplaceChild("head_base", CubeListBuilder.create().texOffs(56, 9)
                .addBox(-0.5F, -1, -0.5F, 1, 1, 1), PartPose.offset(0, -5, 0));

        PartDefinition tongueBase = head.addOrReplaceChild("tongue_base", CubeListBuilder.create().texOffs(30, 0)
                .addBox(-1.5F, -0.5F, -4, 3, 1, 4), PartPose.offsetAndRotation(0, 0, 0, -Mth.HALF_PI, 0, 0));
        PartDefinition tongueMid = tongueBase.addOrReplaceChild("tongue_mid", CubeListBuilder.create().texOffs(34, 0)
                .addBox(-1, -0.5F, -3, 2, 1, 3), PartPose.offsetAndRotation(0, 0, -4, 15.0F * Mth.DEG_TO_RAD, 0, 0));
        tongueMid.addOrReplaceChild("tongue_tip", CubeListBuilder.create().texOffs(36, 0)
                .addBox(-0.5F, -0.5F, -3, 1, 1, 3), PartPose.offsetAndRotation(0, 0, -3, -15.0F * Mth.DEG_TO_RAD, 0, 0));

        PartDefinition lowerBase = head.addOrReplaceChild("mouth_base_lower", CubeListBuilder.create().texOffs(0, 9)
                .addBox(-3, -0.5F, -4, 6, 1, 4), PartPose.offsetAndRotation(0, 0, -0.5F, -60.0F * Mth.DEG_TO_RAD, 0, 0));
        PartDefinition lower = lowerBase.addOrReplaceChild("mouth_lower", CubeListBuilder.create().texOffs(26, 25)
                .addBox(-3, -0.5F, -6, 6, 1, 6), PartPose.offsetAndRotation(0, 0, -4, -25.0F * Mth.DEG_TO_RAD, 0, 0));
        lower.addOrReplaceChild("mouth_lower_bottom_1", CubeListBuilder.create().texOffs(0, 18)
                .addBox(-2, -0.5F, -5, 4, 1, 5), PartPose.offset(0, 1, 0));
        lower.addOrReplaceChild("mouth_lower_bottom_2", CubeListBuilder.create().texOffs(0, 14)
                .addBox(-1, -0.5F, -3, 2, 1, 3), PartPose.offset(0, 2, 0));

        PartDefinition upperBase = head.addOrReplaceChild("mouth_base_upper", CubeListBuilder.create().texOffs(0, 5)
                .addBox(-3, -3, -0.5F, 6, 3, 1), PartPose.offsetAndRotation(0, 0, 0.5F, -30.0F * Mth.DEG_TO_RAD, 0, 0));
        PartDefinition upper = upperBase.addOrReplaceChild("mouth_upper", CubeListBuilder.create().texOffs(0, 24)
                .addBox(-3, -0.5F, -7, 6, 1, 7), PartPose.offsetAndRotation(0, -3, 0, -70.0F * Mth.DEG_TO_RAD, 0, 0));
        upper.addOrReplaceChild("mouth_upper_top_1", CubeListBuilder.create().texOffs(26, 18)
                .addBox(-3, -0.5F, -6, 6, 1, 6), PartPose.offset(0, -1, 0));
        upper.addOrReplaceChild("mouth_upper_top_2", CubeListBuilder.create().texOffs(26, 13)
                .addBox(-2, -0.5F, -4, 4, 1, 4), PartPose.offset(0, -2, -1));

        if (fire) {
            PartDefinition gland = stem3.addOrReplaceChild("gland", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-1.5F, -1.5F, -1.5F, 2, 3, 2), PartPose.offset(0, -2.8F, -1.3F));
            for (int i = 1; i < 6; i++) {
                float angle = (i == 5 ? 30.0F : i * -30.0F) * Mth.DEG_TO_RAD;
                gland.addOrReplaceChild("gland_side_" + i, CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-1.5F, -1.5F, -1.5F, 2, 3, 2), PartPose.rotation(0, angle, 0));
            }
            for (int i = 0; i < 4; i++) {
                gland.addOrReplaceChild("gland_top_" + i, CubeListBuilder.create().texOffs(0, 0)
                                .addBox(-1.5F, -0.5F, -0.5F, 3, 1, 1),
                        PartPose.offsetAndRotation(0, i > 1 ? 1.4F : -1.4F, -0.5F,
                                i % 2 == 1 ? Mth.PI / 4.0F : 0, 0, 0));
            }
        }

        PartDefinition base = root.addOrReplaceChild("base", CubeListBuilder.create().texOffs(44, 0).addBox(-1.5F, 0, -1.5F, 3, 2, 3), PartPose.offset(0, 22, 0));
        addDekuLeaf(base, "leaf_east", 2, 2, 0, false, 0);
        addDekuLeaf(base, "leaf_west", -2, 2, 0, false, Mth.PI);
        addDekuLeaf(base, "leaf_south", 0, 2, 2, true, 0);
        addDekuLeaf(base, "leaf_north", 0, 2, -2, true, Mth.PI);
        if (withered) {
            addDekuThorns(stem1, "thorn_1", 8, 10.0F);
            addDekuThorns(stem2, "thorn_2", 5, 5.0F);
            addDekuThorns(stem3, "thorn_3", 4, 4.0F);
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    private static void addDekuLeaf(PartDefinition base, String name, float x, float y, float z,
                                    boolean alongZ, float yaw) {
        CubeListBuilder firstCube = CubeListBuilder.create().texOffs(50, 16);
        if (alongZ) firstCube.addBox(-2.5F, -3, 0, 5, 3, 1);
        else firstCube.addBox(0, -3, -2.5F, 1, 3, 5);
        PartDefinition first = base.addOrReplaceChild(name + "_base", firstCube,
                PartPose.offsetAndRotation(x, y, z, alongZ ? -25.0F * Mth.DEG_TO_RAD : 0, yaw,
                        alongZ ? 0 : (x < 0 ? -25.0F : 25.0F) * Mth.DEG_TO_RAD));
        CubeListBuilder middleCube = CubeListBuilder.create().texOffs(50, 14);
        if (alongZ) middleCube.addBox(-2, -3, 0, 4, 3, 1);
        else middleCube.addBox(0, -3, -2, 1, 3, 4);
        PartDefinition middle = first.addOrReplaceChild(name + "_mid", middleCube,
                PartPose.offsetAndRotation(0, -3, 0, alongZ ? -30.0F * Mth.DEG_TO_RAD : 0, 0,
                        alongZ ? 0 : 30.0F * Mth.DEG_TO_RAD));
        CubeListBuilder tipCube = CubeListBuilder.create().texOffs(50, 14);
        if (alongZ) tipCube.addBox(-1.5F, -5, 0, 3, 5, 1);
        else tipCube.addBox(0, -5, -1.5F, 1, 5, 3);
        middle.addOrReplaceChild(name + "_tip", tipCube,
                PartPose.offsetAndRotation(0, -3, 0, alongZ ? -30.0F * Mth.DEG_TO_RAD : 0, 0,
                        alongZ ? 0 : 30.0F * Mth.DEG_TO_RAD));
    }

    private static void addDekuThorns(PartDefinition stem, String prefix, int count, float length) {
        for (int i = 0; i < count; i++) {
            double angle = i * Mth.TWO_PI / count;
            float y = -0.8F - (i + 1) * length / (count + 1);
            stem.addOrReplaceChild(prefix + "_" + i, CubeListBuilder.create().texOffs(48, 1)
                            .addBox(-0.5F, -0.5F, -0.5F, 1, 1, 1),
                    PartPose.offsetAndRotation((float)Math.cos(angle) * 0.7F, y,
                            (float)Math.sin(angle) * 0.7F, 40.0F * Mth.DEG_TO_RAD,
                            (float)angle, 40.0F * Mth.DEG_TO_RAD));
        }
    }

    public static LayerDefinition keese() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3, -3, -3, 6, 6, 6)
                .texOffs(24, 0).addBox(-4, -6, -2, 3, 4, 1)
                .texOffs(24, 0).mirror().addBox(1, -6, -2, 3, 4, 1), PartPose.offset(0, 4, 0));
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16).addBox(-3, 0, -3, 6, 12, 6), PartPose.offset(0, 8, 0));
        root.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(42, 0).addBox(-12, 0, 0, 12, 16, 1), PartPose.offset(-2, 8, 1.5F));
        root.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(42, 0).mirror().addBox(0, 0, 0, 12, 16, 1), PartPose.offset(2, 8, 1.5F));
        return LayerDefinition.create(mesh, 64, 64);
    }
}

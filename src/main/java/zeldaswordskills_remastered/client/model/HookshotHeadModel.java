package zeldaswordskills_remastered.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

public final class HookshotHeadModel {
    private HookshotHeadModel() {
    }

    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("shaft", CubeListBuilder.create().texOffs(0, 10)
                .addBox(-0.5F, -0.5F, -4, 1, 1, 6), PartPose.ZERO);
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(14, 10)
                .addBox(-1, -1, -3, 2, 2, 2), PartPose.ZERO);
        root.addOrReplaceChild("top", CubeListBuilder.create().texOffs(22, 10)
                .addBox(-0.5F, -2.5F, -0.5F, 1, 3, 1),
                PartPose.offsetAndRotation(0, 0, -1.8F, 0.3490659F, 0, 0));
        root.addOrReplaceChild("right", CubeListBuilder.create().texOffs(22, 10)
                .addBox(-0.5F, -0.5F, -0.5F, 1, 3, 1),
                PartPose.offsetAndRotation(0, -0.2F, -1.8F, -0.3490659F, 0, 0.9250245F));
        root.addOrReplaceChild("left", CubeListBuilder.create().texOffs(22, 10)
                .addBox(-0.5F, -0.5F, -0.5F, 1, 3, 1),
                PartPose.offsetAndRotation(0, -0.2F, -1.8F, -0.3490659F, 0, -0.9250245F));
        return LayerDefinition.create(mesh, 64, 32);
    }
}

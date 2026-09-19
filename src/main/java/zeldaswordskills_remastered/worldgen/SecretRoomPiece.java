package zeldaswordskills_remastered.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import zeldaswordskills_remastered.registry.ZSSRegistries;

public final class SecretRoomPiece extends TemplateStructurePiece {
    public SecretRoomPiece(StructureTemplateManager manager, ResourceLocation template, BlockPos pos, Rotation rotation) {
        super(ZSSRegistries.SECRET_ROOM_PIECE.get(),0,manager,template,template.toString(),settings(rotation),pos);
    }
    public SecretRoomPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ZSSRegistries.SECRET_ROOM_PIECE.get(),tag,context.structureTemplateManager(),
                id -> settings(Rotation.valueOf(tag.getString("rotation"))));
    }
    private static StructurePlaceSettings settings(Rotation rotation) { return new StructurePlaceSettings().setRotation(rotation); }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context,tag); tag.putString("rotation",getRotation().name());
    }
    @Override protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {}
}

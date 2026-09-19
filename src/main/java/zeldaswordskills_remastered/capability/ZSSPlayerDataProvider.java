package zeldaswordskills_remastered.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The single source of a player's Zelda data.
 * <p>
 * The optional is never invalidated on its own. Vanilla removes the old player before it posts
 * {@code PlayerEvent.Clone}, and the clone handler can only copy that data while
 * {@code Entity#reviveCaps} makes it readable again; a permanently invalidated optional could never
 * be revived. The entity's own capability flag still hides this data from every normal lookup once
 * the player is gone, so nothing is exposed by keeping the optional alive.
 */
public final class ZSSPlayerDataProvider implements ICapabilitySerializable<CompoundTag> {
    private final ZSSPlayerData data = new ZSSPlayerData();
    private final LazyOptional<ZSSPlayerData> optional = LazyOptional.of(() -> data);

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        return capability == ZSSCapabilities.PLAYER_DATA ? optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return data.save();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        data.load(tag);
    }
}

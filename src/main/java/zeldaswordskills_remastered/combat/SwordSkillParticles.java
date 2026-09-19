package zeldaswordskills_remastered.combat;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

final class SwordSkillParticles {
    private SwordSkillParticles() {}

    static void blockBreak(ServerPlayer player, LivingEntity target, BlockState block, int count) {
        var particle = new BlockParticleOption(ParticleTypes.BLOCK, block);
        var bounds = target.getBoundingBox();
        var center = bounds.getCenter();
        var viewers = player.serverLevel().players().stream()
                .filter(viewer -> viewer.distanceToSqr(target) <= 32.0D * 32.0D).toList();
        // Spawn outside the body instead of hiding a Gaussian burst inside its opaque model.
        int perRing = count / 2;
        for (int i = 0; i < count; i++) {
            double angle = (i % perRing) * Math.PI * 2.0D / perRing;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            double edge = Math.max(Math.abs(dx), Math.abs(dz));
            double x = center.x + dx / edge * (bounds.getXsize() * 0.5D + 0.15D);
            double z = center.z + dz / edge * (bounds.getZsize() * 0.5D + 0.15D);
            double y = bounds.minY + bounds.getYsize() * (i < perRing ? 0.35D : 0.7D);
            for (ServerPlayer viewer : viewers) {
                // Count zero preserves the supplied outward velocity; force shows the hit at reduced particle settings.
                player.serverLevel().sendParticles(viewer, particle, true, x, y, z, 0, dx * 0.12D, 0.08D, dz * 0.12D, 1.0D);
            }
        }
    }
}

package zeldaswordskills_remastered.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.Nullable;

public final class CycloneParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    private CycloneParticle(ClientLevel level, double x, double y, double z, double velocityX, double velocityY, double velocityZ, SpriteSet sprites) {
        super(level, x, y, z, velocityX, velocityY, velocityZ);
        this.sprites = sprites;
        lifetime = 20;
        quadSize = 0.5F;
        friction = 0.92F;
        hasPhysics = false;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) {
            quadSize += 0.05F;
            alpha = Math.max(0.0F, 1.0F - (float) age / lifetime);
            setSpriteFromAge(sprites);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Nullable
        @Override
        public CycloneParticle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                               double velocityX, double velocityY, double velocityZ) {
            return new CycloneParticle(level, x, y, z, velocityX, velocityY, velocityZ, sprites);
        }
    }
}

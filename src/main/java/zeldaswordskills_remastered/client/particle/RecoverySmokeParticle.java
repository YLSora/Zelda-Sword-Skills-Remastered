package zeldaswordskills_remastered.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SmokeParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

public final class RecoverySmokeParticle extends SmokeParticle {
    private RecoverySmokeParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0, .06, 0, 1, sprites);
        setColor(.85F, .04F, .04F);
        lifetime = 30;
        xd = 0;
        yd = .06;
        zd = 0;
        hasPhysics = false;
    }

    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites) {
        return (type, level, x, y, z, dx, dy, dz) -> new RecoverySmokeParticle(level, x, y, z, sprites);
    }
}

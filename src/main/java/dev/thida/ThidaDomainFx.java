package dev.thida;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** World-space ritual: no blocks, entities, blindness or gameplay changes. */
public final class ThidaDomainFx {
    private static final DustParticleOptions CURSE = new DustParticleOptions(new Vector3f(.64f, .04f, .22f), 1.6f);
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(.94f, .61f, .18f), 1.2f);
    private static final DustParticleOptions INK = new DustParticleOptions(new Vector3f(.13f, .04f, .21f), 2f);

    public static void open(ServerLevel level, Vec3 center) {
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.6f, .65f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, .6f);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y + 1, center.z, 32, .6, 1, .6, .035);
    }

    public static void tick(ServerLevel level, Vec3 c, double radius, long age) {
        if (age < 0) return;
        if (age == 20) level.playSound(null, c.x, c.y, c.z, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.3f, .6f);
        if (age == 60) level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, .8f, .6f);
        if (age < 60) {
            if (age % 4 != 0) return;
            double progress = Math.min(1, (age + 4) / 60.0);
            double wave = radius * (1 - Math.pow(1 - progress, 3));
            ring(level, c, wave, .15, 80, CURSE, age * .025);
            ring(level, c, Math.max(.5, wave - 1), .3, 48, GOLD, -age * .025);
            // Twelve rising threads weave the barrier shut over three seconds.
            for (int spoke = 0; spoke < 12; spoke++) {
                double angle = spoke * Math.PI / 6;
                for (int j = 0; j < 6; j++) {
                    double elevation = Math.PI / 2 * Math.max(0, progress - j * .035);
                    double horizontal = radius * Math.cos(elevation);
                    point(level, j % 2 == 0 ? CURSE : INK, c,
                            Math.cos(angle) * horizontal, Math.sin(elevation) * radius, Math.sin(angle) * horizontal);
                }
            }
        } else if (age % 40 == 0) {
            ring(level, c, radius, .15, 96, CURSE, age * .003);
            ring(level, c, 3.5, .18, 32, GOLD, -age * .012);
            // Broken meridians keep the spherical range visible without a solid wall.
            for (int axis = 0; axis < 4; axis++) {
                double angle = axis * Math.PI / 4 + age * .001;
                for (int j = 0; j < 24; j++) {
                    double arc = j * Math.PI * 2 / 24 + age * .004;
                    point(level, j % 3 == 0 ? GOLD : INK, c,
                            radius * Math.cos(arc) * Math.cos(angle), radius * Math.sin(arc),
                            radius * Math.cos(arc) * Math.sin(angle));
                }
            }
            // Six ritual stitches around the caster's original position.
            for (int rune = 0; rune < 6; rune++) {
                double a = rune * Math.PI / 3;
                for (int j = -2; j <= 2; j++) {
                    double r = 4.5 + j * .25;
                    point(level, GOLD, c, Math.cos(a) * r, .2, Math.sin(a) * r);
                    point(level, CURSE, c, Math.cos(a) * 4.5 - Math.sin(a) * j * .25,
                            .2, Math.sin(a) * 4.5 + Math.cos(a) * j * .25);
                }
            }
        }
    }

    public static void close(ServerLevel level, Vec3 c, double radius) {
        ring(level, c, radius, .2, 80, ParticleTypes.SMOKE, 0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2f, .6f);
    }

    private static void ring(ServerLevel level, Vec3 c, double r, double y, int count, ParticleOptions particle, double phase) {
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count + phase;
            point(level, particle, c, Math.cos(angle) * r, y, Math.sin(angle) * r);
        }
    }

    private static void point(ServerLevel level, ParticleOptions particle, Vec3 c, double x, double y, double z) {
        level.sendParticles(particle, c.x + x, c.y + y, c.z + z, 1, 0, 0, 0, 0);
    }
    private ThidaDomainFx() {}
}

package dev.thida;

import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shared aiming geometry. The server still validates every selection independently. */
final class ThidaRuenTargeting {
  private ThidaRuenTargeting() {}

  static boolean eligible(Player owner, Player target, Vec3 center, double reach) {
    return target != owner
        && target.level() == owner.level()
        && target.isAlive()
        && !target.isSpectator()
        && !target.isInvisible()
        && target.position().distanceToSqr(center)
            <= ThidaRuenSaneha.RADIUS * ThidaRuenSaneha.RADIUS
        && owner.getEyePosition().distanceToSqr(target.getEyePosition()) <= reach * reach;
  }

  static boolean visible(Player owner, Vec3 point) {
    return owner
            .level()
            .clip(
                new ClipContext(
                    owner.getEyePosition(),
                    point,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    owner))
            .getType()
        == HitResult.Type.MISS;
  }

  static <P extends Player> P aimed(
      Player owner, Iterable<P> players, double reach, Predicate<P> eligible) {
    Vec3 eye = owner.getEyePosition();
    Vec3 end = eye.add(owner.getLookAngle().scale(reach));
    P nearest = null;
    Vec3 hit = null;
    double best = reach * reach;
    for (P target : players) {
      if (!eligible.test(target)) continue;
      var box = target.getBoundingBox();
      Vec3 candidate = box.contains(eye) ? eye : box.clip(eye, end).orElse(null);
      if (candidate == null || eye.distanceToSqr(candidate) >= best) continue;
      best = eye.distanceToSqr(candidate);
      nearest = target;
      hit = candidate;
    }
    return hit != null && visible(owner, hit) ? nearest : null;
  }
}

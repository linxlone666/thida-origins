package dev.thida;

public final class ThidaDamageMath {
    public record Split(float owner, float target) {}
    public record Health(float owner, float target) {}
    /** Average current HP, then cap independently; overflow is not transferred. */
    public static Health initialHealth(float ownerHp, float ownerMax, float targetHp, float targetMax) {
        if (ownerHp >= 25 && targetHp >= 25) return new Health(ownerHp, targetHp);
        float average = (ownerHp + targetHp) / 2;
        return new Health(Math.min(average, ownerMax), Math.min(average, targetMax));
    }
    public static Split equalize(float ownerHp, float targetHp, float damage) {
        float total = Math.max(0, damage), gap = Math.abs(ownerHp - targetHp);
        float higher = Math.min(total, gap) + Math.max(0, total - gap) / 2;
        float lower = total - higher;
        return ownerHp >= targetHp ? new Split(higher, lower) : new Split(lower, higher);
    }
    private ThidaDamageMath() {}
}

package dev.thida;

import java.util.Arrays;
import java.util.Optional;

/** Stable storage keys and the two historical spell-number conventions meet here. */
public enum ThidaSpell {
  NAROTRA_SHIELD("narotra_doll", 0),
  NAROTRA_CLEANSE("narotra_doll", 1),
  NAROTRA_RENEWAL("narotra_doll", 2),
  NILAKAN_WEAKNESS("nilakan_doll", 0),
  NILAKAN_BLEED("nilakan_doll", 1),
  NILAKAN_VULNERABILITY("nilakan_doll", 2);

  private final String doll;
  private final int index;

  ThidaSpell(String doll, int index) {
    this.doll = doll;
    this.index = index;
  }

  public String doll() {
    return doll;
  }

  public int storageIndex() {
    return index;
  }

  public int documentNumber() {
    return index + 2;
  }

  public String id() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }

  public static Optional<ThidaSpell> find(String doll, int index) {
    return Arrays.stream(values())
        .filter(spell -> spell.doll.equals(doll) && spell.index == index)
        .findFirst();
  }
}

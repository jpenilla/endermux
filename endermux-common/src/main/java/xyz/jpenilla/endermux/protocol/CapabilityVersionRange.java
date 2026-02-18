package xyz.jpenilla.endermux.protocol;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record CapabilityVersionRange(int min, int max) {

  public boolean isValid() {
    return this.min > 0 && this.max >= this.min;
  }

  public boolean includes(final int version) {
    return this.isValid() && version >= this.min && version <= this.max;
  }

  public @Nullable Integer highestCommonVersion(final CapabilityVersionRange other) {
    if (!this.isValid() || !other.isValid()) {
      return null;
    }
    final int commonMin = Math.max(this.min, other.min);
    final int commonMax = Math.min(this.max, other.max);
    return commonMax >= commonMin ? commonMax : null;
  }
}

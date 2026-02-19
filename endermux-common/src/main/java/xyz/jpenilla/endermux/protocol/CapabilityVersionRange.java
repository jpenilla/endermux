package xyz.jpenilla.endermux.protocol;

import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record CapabilityVersionRange(int min, int max, Set<Integer> exclude) {

  public CapabilityVersionRange(final int min, final int max) {
    this(min, max, Set.of());
  }

  public CapabilityVersionRange {
    exclude = exclude == null ? Set.of() : Set.copyOf(exclude);
  }

  public boolean isValid() {
    if (this.min <= 0 || this.max < this.min) {
      return false;
    }
    for (final Integer version : this.exclude) {
      if (version == null || version < this.min || version > this.max) {
        return false;
      }
    }
    return true;
  }

  public boolean includes(final int version) {
    return this.isValid()
      && version >= this.min
      && version <= this.max
      && !this.exclude.contains(version);
  }

  public @Nullable Integer highestCommonVersion(final CapabilityVersionRange other) {
    if (!this.isValid() || !other.isValid()) {
      return null;
    }
    final int commonMin = Math.max(this.min, other.min);
    final int commonMax = Math.min(this.max, other.max);
    if (commonMax < commonMin) {
      return null;
    }
    for (int version = commonMax; version >= commonMin; version--) {
      if (this.includes(version) && other.includes(version)) {
        return version;
      }
    }
    return null;
  }
}

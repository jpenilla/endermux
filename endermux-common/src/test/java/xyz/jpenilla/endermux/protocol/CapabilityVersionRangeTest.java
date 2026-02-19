package xyz.jpenilla.endermux.protocol;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityVersionRangeTest {

  @Test
  void includesRespectsExcludeSet() {
    final CapabilityVersionRange range = new CapabilityVersionRange(1, 5, Set.of(3));
    assertTrue(range.isValid());
    assertTrue(range.includes(2));
    assertFalse(range.includes(3));
    assertTrue(range.includes(5));
  }

  @Test
  void invalidWhenExcludeOutsideBounds() {
    assertFalse(new CapabilityVersionRange(2, 4, Set.of(1)).isValid());
    assertFalse(new CapabilityVersionRange(2, 4, Set.of(5)).isValid());
  }

  @Test
  void highestCommonVersionSkipsExcludedValues() {
    final CapabilityVersionRange left = new CapabilityVersionRange(1, 5, Set.of(5));
    final CapabilityVersionRange right = new CapabilityVersionRange(3, 6, Set.of(4));
    assertEquals(3, left.highestCommonVersion(right));
  }

  @Test
  void highestCommonVersionNullWhenNoIncludedOverlap() {
    final CapabilityVersionRange left = new CapabilityVersionRange(1, 4, Set.of(3, 4));
    final CapabilityVersionRange right = new CapabilityVersionRange(3, 5, Set.of(3, 4));
    assertNull(left.highestCommonVersion(right));
  }
}

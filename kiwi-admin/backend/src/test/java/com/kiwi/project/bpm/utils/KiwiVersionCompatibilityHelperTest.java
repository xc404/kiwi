package com.kiwi.project.bpm.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KiwiVersionCompatibilityHelperTest {

  private final KiwiVersionCompatibilityHelper helper = new KiwiVersionCompatibilityHelper();

  @Test
  void compatibleWhenEqual() {
    assertTrue(helper.isCompatible("1.0.0-SNAPSHOT", "1.0.0"));
  }

  @Test
  void compatibleWhenHigherPatch() {
    assertTrue(helper.isCompatible("1.0.1", "1.0.0"));
  }

  @Test
  void incompatibleWhenLowerMinor() {
    assertFalse(helper.isCompatible("1.0.0", "1.1.0"));
  }

  @Test
  void blankRequiredIsCompatible() {
    assertTrue(helper.isCompatible("0.9.0", null));
  }
}

package xyz.jpenilla.endermux.protocol;

import org.jspecify.annotations.NullMarked;

@NullMarked
public enum FrameCompressionType {
  NONE(0),
  GZIP(1);

  private final int wireValue;

  FrameCompressionType(final int wireValue) {
    if (wireValue < 0 || wireValue > 0xFF) {
      throw new IllegalArgumentException("wireValue must be in range 0..255");
    }
    this.wireValue = wireValue;
  }

  public int wireValue() {
    return this.wireValue;
  }

  public static FrameCompressionType fromWireValue(final int wireValue) throws ProtocolException {
    for (final FrameCompressionType value : values()) {
      if (value.wireValue == wireValue) {
        return value;
      }
    }
    throw new ProtocolException("Unsupported frame compression: " + wireValue);
  }
}

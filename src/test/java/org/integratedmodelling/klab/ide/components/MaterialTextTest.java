package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MaterialTextTest {
  @Test void savesPreserveUnicodeEncodingBomAndLineEndings() {
    for (var charset : java.util.List.of(StandardCharsets.UTF_8, StandardCharsets.UTF_16LE, StandardCharsets.UTF_16BE)) {
      byte[] bom = charset.equals(StandardCharsets.UTF_8) ? new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf}
          : charset.equals(StandardCharsets.UTF_16LE) ? new byte[] {(byte) 0xff, (byte) 0xfe} : new byte[] {(byte) 0xfe, (byte) 0xff};
      var original = new MaterialText("España\r\n", charset, bom);
      var restored = MaterialText.decode(original.encode(original.source()));
      assertNotNull(restored); assertEquals(original.source(), restored.source());
      assertArrayEquals(original.encode("Edited\r\n"), restored.encode("Edited\r\n"));
    }
  }
}

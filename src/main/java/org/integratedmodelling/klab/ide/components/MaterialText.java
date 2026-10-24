package org.integratedmodelling.klab.ide.components;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Lossless Unicode source transport, preserving a file's encoding and byte-order mark. */
record MaterialText(String source, Charset charset, byte[] bom) {
  static MaterialText decode(byte[] bytes) {
    if (bytes == null) return null;
    Charset charset = StandardCharsets.UTF_8;
    int prefix = 0;
    if (bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf) prefix = 3;
    else if (bytes.length >= 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe) { charset = StandardCharsets.UTF_16LE; prefix = 2; }
    else if (bytes.length >= 2 && bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff) { charset = StandardCharsets.UTF_16BE; prefix = 2; }
    try {
      String source = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, prefix, bytes.length - prefix)).toString();
      if (source.chars().anyMatch(c -> c < 32 && c != '\n' && c != '\r' && c != '\t' && c != '\f')) return null;
      return new MaterialText(source, charset, Arrays.copyOf(bytes, prefix));
    } catch (java.nio.charset.CharacterCodingException binary) { return null; }
  }

  byte[] encode(String source) {
    byte[] content = source.getBytes(charset);
    byte[] result = Arrays.copyOf(bom, bom.length + content.length);
    System.arraycopy(content, 0, result, bom.length, content.length);
    return result;
  }
}

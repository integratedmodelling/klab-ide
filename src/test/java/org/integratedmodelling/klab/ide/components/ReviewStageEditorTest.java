package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URL;
import org.junit.jupiter.api.Test;

class ReviewStageEditorTest {
  @Test void linksOnlyToSelectedServiceAndEncodesIdentifiers() throws Exception {
    assertEquals("https://resources.example/base/ui/review?flow=a%26b&stage=s%2F1",
        ReviewStageEditor.reviewUri(new URL("https://resources.example/base/"), "a&b", "s/1").toString());
  }
  @Test void rejectsCredentialAndNonHttpUrls() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> ReviewStageEditor.reviewUri(null, "f", "s"));
    assertThrows(IllegalArgumentException.class, () -> ReviewStageEditor.reviewUri(new URL("file:/tmp/ui"), "f", "s"));
    assertThrows(IllegalArgumentException.class, () -> ReviewStageEditor.reviewUri(new URL("https://user:secret@resources.example"), "f", "s"));
    assertThrows(IllegalArgumentException.class, () -> ReviewStageEditor.reviewUri(new URL("https://resources.example?redirect=other"), "f", "s"));
  }
}

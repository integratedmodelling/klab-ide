package org.integratedmodelling.klab.ide.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NotificationCardTest {

  @Test
  void shortMessagesRemainUnchangedAndCollapsed() {
    assertEquals("A short message", NotificationCard.preview("  A short   message  "));
    assertFalse(NotificationCard.isExpandable("A short message"));
  }

  @Test
  void longMessagesHaveABoundedPreviewButRemainExpandable() {
    var message = "x".repeat(NotificationCard.PREVIEW_CHARACTERS + 20);

    assertTrue(NotificationCard.isExpandable(message));
    assertEquals(
        NotificationCard.PREVIEW_CHARACTERS + 1, NotificationCard.preview(message).length());
    assertTrue(NotificationCard.preview(message).endsWith("\u2026"));
  }

  @Test
  void multilineMessagesAreExpandableAndPreviewedOnOneLine() {
    assertTrue(NotificationCard.isExpandable("First line\nSecond line"));
    assertEquals("First line Second line", NotificationCard.preview("First line\nSecond line"));
  }
}

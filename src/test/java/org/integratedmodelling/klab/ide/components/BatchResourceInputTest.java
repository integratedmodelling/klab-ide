package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor;
import org.junit.jupiter.api.Test;

class BatchResourceInputTest {

  private final BatchResourceInput.AdapterOption adapter =
      new BatchResourceInput.AdapterOption(null, new AdapterDescriptor(), "Resources");

  @Test
  void requiresAnAdapterAndEitherAUrlOrUploadedContent() {
    assertFalse(BatchResourceInput.canAccept(null, "https://example.org/data", false));
    assertFalse(BatchResourceInput.canAccept(adapter, "  ", false));
    assertTrue(BatchResourceInput.canAccept(adapter, "https://example.org/data", false));
    assertTrue(BatchResourceInput.canAccept(adapter, "", true));
  }
}

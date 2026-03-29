package org.integratedmodelling.klab.ide.components.generic;

import org.integratedmodelling.klab.api.cli.FormattedString;

public enum BBCodeRenderer implements FormattedString.Renderer {
  INSTANCE;

  @Override
  public String render(FormattedString.Fragment fragment) {
    return fragment.text();
  }
}

package org.integratedmodelling.klab.ide.components.generic;

import org.integratedmodelling.klab.api.cli.FormattedString;
import org.integratedmodelling.klab.api.utils.Utils;

public enum BBCodeRenderer implements FormattedString.Renderer {
  INSTANCE;

  @Override
  public String render(FormattedString.Fragment fragment) {

    var ret = "";

    if (fragment.color() != null) {
      ret += "[color=" + Utils.Colors.encodeRGB(fragment.color()) + "]";
    }

    if (fragment.style() != null) {
      ret +=
          switch (fragment.style()) {
            case BOLD -> "[b]" + fragment.text() + "[/b]";
            case ITALIC -> "[i]" + fragment.text() + "[/i]";
            case UNDERLINE -> "[u]" + fragment.text() + "[/u]";
            case STRIKETHROUGH -> "[s]" + fragment.text() + "[/s]";
          };
    } else {
      ret += fragment.text();
    }

    if (fragment.color() != null) {
      ret += "[/color]";
    }

    return ret;
  }
}

package org.integratedmodelling.klab.ide.components.generic;

import org.integratedmodelling.klab.api.cli.FormattedString;

public enum BBCodeRenderer implements FormattedString.Renderer {
  INSTANCE;

  @Override
  public String render(FormattedString.Fragment fragment) {

    var ret = fragment.text();

    if (fragment.color() != null) {
      // TODO needs a strategy to look up the closest color among an array
//      ret =
//          "[color="
//              + fragment.color().name().toLowerCase()
//              + "]"
//              + ret
//              + "[/color]";
    }

    if (fragment.style() != null) {
      switch (fragment.style()) {
        case BOLD:
          ret = "[b]" + ret + "[/b]";
          break;
        case ITALIC:
          ret = "[i]" + ret + "[/i]";
          break;
        case UNDERLINE:
          ret = "[u]" + ret + "[/u]";
          break;
        case STRIKETHROUGH:
          ret = "[s]" + ret + "[/s]";
          break;
      }
    }

    return ret;
  }
}

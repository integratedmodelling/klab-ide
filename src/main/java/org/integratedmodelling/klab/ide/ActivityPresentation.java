package org.integratedmodelling.klab.ide;

import java.util.Locale;
import org.integratedmodelling.klab.api.provenance.Activity;

/** Null-safe labels shared by tree, inspector and activity cards. */
public final class ActivityPresentation {
  private ActivityPresentation() {}

  public static String type(Activity activity) {
    if (activity == null || activity.getType() == null) return "Activity";
    var text = activity.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }

  public static String description(Activity activity) {
    return activity == null || activity.getDescription() == null
        || activity.getDescription().isBlank() ? type(activity) : activity.getDescription();
  }
}

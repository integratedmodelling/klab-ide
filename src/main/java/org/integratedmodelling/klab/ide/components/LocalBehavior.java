package org.integratedmodelling.klab.ide.components;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsActionImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;

/**
 * Lightweight syntax model used while a standalone behavior is outside a workspace. Wow this is
 * ingenious.
 *
 * <p>TODO substitute with actual parser from resources service!
 */
public final class LocalBehavior {

  private static final Pattern HEADER =
      Pattern.compile(
          "(?m)^\\s*(behavior|app|application|script|task|testcase|component|traits)\\s+([\\w.-]+)");
  private static final Pattern ACTION =
      Pattern.compile("(?m)^\\s*(action|function)\\s+([\\w.-]+)\\s*(?:\\(([^)]*)\\))?\\s*:");

  private LocalBehavior() {}

  public static KActorsBehavior parse(Path file, String source) {
    var behavior = new KActorsBehaviorImpl();
    Matcher header = HEADER.matcher(source);
    String fallback = stripExtension(file.getFileName().toString());
    boolean hasHeader = header.find();
    behavior.setUrn(hasHeader ? header.group(2) : fallback);
    behavior.setBehaviorType(hasHeader ? type(header.group(1)) : KActorsBehavior.Type.BEHAVIOR);
    behavior.setPlatform(KActorsBehavior.Platform.ANY);
    behavior.setSourceCode(source);
    behavior.setProjectName(null);
    behavior.setCreationTimestamp(file.toFile().lastModified());
    behavior.setLastUpdateTimestamp(file.toFile().lastModified());

    var actions = new ArrayList<KActorsAction>();
    Matcher matcher = ACTION.matcher(source);
    while (matcher.find()) {
      var action = new KActorsActionImpl();
      action.setUrn(matcher.group(2));
      //      action.setFunction("function".equals(matcher.group(1)));
      action.setOffsetInDocument(matcher.start());
      action.setLength(matcher.end() - matcher.start());
      if (matcher.group(3) != null && !matcher.group(3).isBlank()) {
        action.setArgumentNames(
            java.util.Arrays.stream(matcher.group(3).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
      }
      actions.add(action);
    }
    behavior.setStatements(actions);
    return behavior;
  }

  private static KActorsBehavior.Type type(String keyword) {
    if (keyword == null) return KActorsBehavior.Type.BEHAVIOR;
    return switch (keyword.toLowerCase(Locale.ROOT)) {
      case "app", "application" -> KActorsBehavior.Type.APP;
      case "script" -> KActorsBehavior.Type.SCRIPT;
      case "task" -> KActorsBehavior.Type.TASK;
      case "testcase" -> KActorsBehavior.Type.UNITTEST;
      case "component" -> KActorsBehavior.Type.COMPONENT;
      case "traits" -> KActorsBehavior.Type.TRAIT;
      default -> KActorsBehavior.Type.BEHAVIOR;
    };
  }

  public static String stripExtension(String name) {
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }
}

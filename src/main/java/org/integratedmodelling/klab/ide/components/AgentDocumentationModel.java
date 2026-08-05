package org.integratedmodelling.klab.ide.components;

import java.util.Locale;
import org.integratedmodelling.klab.api.collections.DomainObject;

/** Toolkit-independent schema, search support, and mock source for agent documentation. */
public final class AgentDocumentationModel {

  public static final String AGENT_DOCUMENTATION = "agent-documentation";
  public static final String AGENT = "agent";
  public static final String VERB = "verb";
  public static final String DOCUMENTATION = "documentation";
  public static final String MARKDOWN = "markdown";
  public static final String SYNTAX = "syntax";
  public static final String SERVICE_ID = "serviceId";
  public static final String JAVA_CLASS = "javaClass";
  public static final String SINCE = "since";

  private AgentDocumentationModel() {}

  public static boolean matches(String query, DomainObject object) {
    if (object == null || query == null || query.isBlank()) {
      return false;
    }
    return searchableText(object)
        .toLowerCase(Locale.ROOT)
        .contains(query.toLowerCase(Locale.ROOT));
  }

  static DomainObject documentationNode(DomainObject verb) {
    return DomainObject.create(
        DomainObject.TYPE,
        DOCUMENTATION,
        DomainObject.NAME,
        verb.name(),
        MARKDOWN,
        verb.get(MARKDOWN, String.class),
        "searchText",
        searchableText(verb));
  }

  private static String searchableText(DomainObject object) {
    return String.join(
        " ",
        text(object.type()),
        text(object.name()),
        text(object.label()),
        text(object.urn()),
        text(object.description()),
        text(object.get(SYNTAX)),
        text(object.get("aliases")),
        text(object.get("searchText")));
  }

  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  /** Mock service response documenting the intended bean fields and hierarchy. */
  public static DomainObject mockDocumentation() {
    var observe =
        DomainObject.create(
            DomainObject.TYPE,
            VERB,
            DomainObject.NAME,
            "observe",
            DomainObject.LABEL,
            "Observe",
            DomainObject.URN,
            "klab.runtime.observe",
            DomainObject.DESCRIPTION,
            "Submit an observable and return the resulting observation.",
            SYNTAX,
            "observe observable [within geometry]",
            "aliases",
            "resolve contextualize",
            SINCE,
            "1.0",
            MARKDOWN,
            "## observe\n\nSubmits an observable to the current context.\n\n"
                + "### Parameters\n\n- `observable`: observable URN or declaration\n"
                + "- `within`: optional geometry\n\n### Returns\n\nThe resolved observation.");
    var emit =
        DomainObject.create(
            DomainObject.TYPE,
            VERB,
            DomainObject.NAME,
            "emit",
            DomainObject.LABEL,
            "Emit event",
            DomainObject.URN,
            "klab.runtime.emit",
            DomainObject.DESCRIPTION,
            "Send an event through the agent communication channel.",
            SYNTAX,
            "emit event [with payload]",
            "aliases",
            "send publish",
            SINCE,
            "1.0",
            MARKDOWN,
            "## emit\n\nPublishes an event from the active agent.\n\n"
                + "### Example\n\n```kactors\nemit observation_ready with result\n```");
    var runtimeAgent =
        DomainObject.create(
            DomainObject.TYPE,
            AGENT,
            DomainObject.NAME,
            "Runtime agent",
            DomainObject.LABEL,
            "Runtime",
            DomainObject.URN,
            "klab.runtime",
            DomainObject.DESCRIPTION,
            "Core verbs available to behaviors running in a runtime scope.",
            SERVICE_ID,
            "runtime",
            JAVA_CLASS,
            "org.integratedmodelling.klab.runtime.RuntimeAgent",
            observe,
            emit);

    var log =
        DomainObject.create(
            DomainObject.TYPE,
            VERB,
            DomainObject.NAME,
            "log",
            DomainObject.LABEL,
            "Log message",
            DomainObject.URN,
            "klab.system.log",
            DomainObject.DESCRIPTION,
            "Write a structured message to the execution log.",
            SYNTAX,
            "log message [as level]",
            "aliases",
            "info warn error",
            SINCE,
            "1.0",
            MARKDOWN,
            "## log\n\nWrites a message using the requested severity.\n\n"
                + "Supported levels are `debug`, `info`, `warning`, and `error`.");
    var systemAgent =
        DomainObject.create(
            DomainObject.TYPE,
            AGENT,
            DomainObject.NAME,
            "System agent",
            DomainObject.LABEL,
            "System",
            DomainObject.URN,
            "klab.system",
            DomainObject.DESCRIPTION,
            "General system and diagnostic verbs.",
            SERVICE_ID,
            "runtime",
            JAVA_CLASS,
            "org.integratedmodelling.klab.runtime.SystemAgent",
            log);

    return DomainObject.create(
        DomainObject.TYPE,
        AGENT_DOCUMENTATION,
        DomainObject.NAME,
        "Agent documentation",
        DomainObject.DESCRIPTION,
        "Documentation advertised by connected services",
        runtimeAgent,
        systemAgent);
  }
}

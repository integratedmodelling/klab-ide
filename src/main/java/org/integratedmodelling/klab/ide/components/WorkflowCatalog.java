package org.integratedmodelling.klab.ide.components;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowVersions;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;

/** Start menus show one current definition per ID; saved flows keep their pinned definition. */
public final class WorkflowCatalog {
  private WorkflowCatalog() {}

  public static List<Workflow> latest(List<Workflow> definitions) {
    var latest = new LinkedHashMap<String, Workflow>();
    if (definitions == null) return List.of();
    for (var workflow : definitions) {
      if (workflow == null || workflow.getId() == null) continue;
      latest.merge(workflow.getId(), workflow,
          (left, right) -> compare(left.getVersion(), right.getVersion()) >= 0 ? left : right);
    }
    return latest.values().stream().sorted(Comparator.comparing(WorkflowCatalog::label)).toList();
  }

  static int compare(String left, String right) {
    return WorkflowVersions.compare(left, right);
  }

  public static String label(Workflow workflow) {
    String name = workflow.getName() == null || workflow.getName().isBlank()
        ? workflow.getId() : workflow.getName();
    return name + " (v" + Objects.toString(workflow.getVersion(), "unknown") + ")";
  }
}

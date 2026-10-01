package org.integratedmodelling.klab.ide;

import java.util.List;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;

/** Adapts navigable document identifiers to the resources service deletion contract. */
final class DocumentDeletion {
  private DocumentDeletion() {}

  static List<ResourceSet> delete(ResourcesService service, NavigableAsset asset, UserScope scope) {
    if (!(asset instanceof KlabDocument<?> document))
      throw new IllegalArgumentException("Only documents can be deleted here");
    var project = document.getProjectName();
    var urn = document.getUrn();
    if (project == null || project.isBlank() || urn == null || urn.isBlank())
      throw new IllegalArgumentException("Document deletion requires a project and document URN");
    // The API routes document deletion by project/document, while tree URNs are unqualified.
    var results = service.delete(project + "/" + urn, KlabAsset.classify(document), scope);
    if (results == null || results.isEmpty())
      return List.of(ResourceSet.empty(Notification.error("No deletion result returned for " + urn)));
    return results;
  }
}

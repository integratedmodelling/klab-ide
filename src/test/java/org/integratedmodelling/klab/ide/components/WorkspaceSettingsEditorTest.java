package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.junit.jupiter.api.Test;

class WorkspaceSettingsEditorTest {
  @Test void visibilityDoesNotGrantEditing() {
    var info = new ResourceInfo(); info.setKnowledgeClass(KnowledgeClass.WORKSPACE);
    info.setPermissions(EnumSet.of(CRUDOperation.READ));
    assertTrue(WorkspaceSettingsEditor.canAudit(info)); assertFalse(WorkspaceSettingsEditor.canEdit(info));
    info.setPermissions(EnumSet.of(CRUDOperation.UPDATE)); assertTrue(WorkspaceSettingsEditor.canEdit(info));
    info.setPermissions(EnumSet.of(CRUDOperation.ADMINISTER)); assertTrue(WorkspaceSettingsEditor.canEdit(info));
    info.setPermissions(EnumSet.noneOf(CRUDOperation.class)); assertFalse(WorkspaceSettingsEditor.canAudit(info));
    assertFalse(WorkspaceSettingsEditor.canAudit(null));
  }
  @Test void requestPreservesStructuredMetadataAndDistinguishesUnchangedFromPrivateRights() {
    var metadata = new HashMap<String,Object>(); metadata.put("custom", Map.of("enabled", true));
    var request = WorkspaceSettingsEditor.request("workspace", metadata, null);
    metadata.clear(); assertEquals(Map.of("enabled", true), request.getMetadata().get("custom"));
    assertNull(request.getPrivileges()); assertTrue(request.getProjects().isEmpty());
    assertEquals("", WorkspaceSettingsEditor.request("workspace", Map.of(), "").getPrivileges().toString());
  }
}

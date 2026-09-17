package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.junit.jupiter.api.Test;

class ProjectSettingsEditorTest {
  @Test void textAndStructuredMetadataRemainDistinct() {
    assertEquals("false", ProjectSettingsEditor.parseValue("false", false));
    assertEquals(false, ProjectSettingsEditor.parseValue("false", true));
    var structured = (Map<?, ?>) ProjectSettingsEditor.parseValue("{\"enabled\":true,\"values\":[1,2]}", true);
    assertEquals(true, structured.get("enabled"));
    assertEquals(2, ((List<?>) structured.get("values")).size());
    assertThrows(RuntimeException.class, () -> ProjectSettingsEditor.parseValue("not JSON", true));
  }
  @Test void readAccessDoesNotEnableSettingsEditing() {
    var info = new ResourceInfo(); info.setPermissions(EnumSet.of(CRUDOperation.READ));
    assertFalse(ProjectSettingsEditor.canEdit(info));
    assertFalse(ProjectSettingsEditor.canEdit(null));
    info.setPermissions(EnumSet.of(CRUDOperation.UPDATE));
    assertTrue(ProjectSettingsEditor.canEdit(info));
    info.setPermissions(EnumSet.of(CRUDOperation.ADMINISTER));
    assertTrue(ProjectSettingsEditor.canEdit(info));
  }
  @Test void onlyAdministratorsCanDefineAWorldviewAndOnlyContributorsShowObservers() {
    var info = new ResourceInfo(); info.setPermissions(EnumSet.of(CRUDOperation.UPDATE));
    assertFalse(ProjectSettingsEditor.canAdminister(info));
    info.setPermissions(EnumSet.of(CRUDOperation.ADMINISTER)); assertTrue(ProjectSettingsEditor.canAdminister(info));
    assertFalse(ProjectSettingsEditor.hasWorldview(null)); assertFalse(ProjectSettingsEditor.hasWorldview("  "));
    assertTrue(ProjectSettingsEditor.hasWorldview("earth"));
  }}
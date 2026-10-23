package org.integratedmodelling.klab.ide.components.cards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.integratedmodelling.common.services.RuntimeCapabilitiesImpl;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.ide.IDEContextScope;
import org.junit.jupiter.api.Test;

class QueryViewCardExportTest {
  @Test void mapsPointsAndGeoJsonExportTheCompleteView() {
    var scope = mock(IDEContextScope.class);
    var runtime = mock(RuntimeService.class);
    when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    var view = new ObservationImpl(); view.setId(0);
    when(runtime.exportObservation(same(view), eq("image/png"), any(), same(scope)))
        .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
    when(runtime.exportObservation(same(view), eq("text/plain"), any(), same(scope)))
        .thenReturn(new ByteArrayInputStream("42".getBytes(StandardCharsets.UTF_8)));
    when(runtime.exportObservation(same(view), eq("application/geo+json"), any(), same(scope)))
        .thenReturn(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
    var capabilities = new RuntimeCapabilitiesImpl();
    capabilities.getExportSchemata().put("test", List.of(ResourceTransport.Schema.create(
        "test.point", ResourceTransport.Schema.Type.STREAM,
        KlabAsset.KnowledgeClass.OBSERVATION, "Point").mediaType("text/plain")));
    when(runtime.capabilities(scope)).thenReturn(capabilities);
    var values = ValueCard.Options.runtimeDefaults(scope);
    assertArrayEquals(new byte[] {1, 2, 3}, values.mapImageProvider()
        .load(new ValueCard.MapRequest(view, 0L, 640, 480)).join());
    assertEquals("42", values.pointValueProvider().query(new ValueCard.PointQuery(
        view, 0L, new ValueCard.MapPoint(0.5, 0.5, 1.0, 2.0))).join());
    assertEquals("{}", GeoJsonCard.Options.runtimeDefaults(scope).geoJsonProvider().load(view).join());
    verify(runtime, never()).exportAsset(any(), any(), any(), any(), any());
  }
}

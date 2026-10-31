package org.integratedmodelling.klab.ide;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.common.services.client.scope.ClientContextScope;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.Test;

class ObserverScopeIdentityTest {
  @Test void wrapperDelegatesTransportIdentityWithoutRefreshingButKeepsNormalAccessChecks() throws Exception {
    var delegate = mock(ClientContextScope.class);
    var scope = mock(IDEContextScope.class, CALLS_REAL_METHODS);
    scope.delegate = delegate;
    when(delegate.getId()).thenReturn("session.context");
    when(delegate.getObserverId()).thenReturn(8173L);
    var focus = new ObservationImpl(); focus.setId(8075);
    when(delegate.getContextObservation()).thenReturn(focus);
    when(delegate.getObserver()).thenThrow(new IllegalStateException("observer unavailable"));
    assertEquals("session.context.8075#8173", ContextScope.getScopeId(scope));
    verify(delegate, never()).getObserver();
    assertThrows(IllegalStateException.class, scope::getObserver);
    when(delegate.getObserverId()).thenReturn(null);
    assertEquals("session.context.8075", ContextScope.getScopeId(scope));
  }
}

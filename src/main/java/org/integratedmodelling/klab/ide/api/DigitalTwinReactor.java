package org.integratedmodelling.klab.ide.api;

import org.integratedmodelling.klab.ide.model.IDEContextScope;

/**
 * A DT reactor is a component that reacts to digital twins. As such it gets informed of when a new
 * one is created (either locally or within a federation) or set into focus through a UI action. A
 * new digital twin may be in focus in the UI or not.
 */
public interface DigitalTwinReactor {

  /**
   * @param scope
   * @param inFocus
   */
  void setDigitalTwin(IDEContextScope scope, boolean inFocus);

  /**
   * Each view must call this when the component is closed. In turn, the component must close all
   * the viewers it may have created, so that their scope listeners are unregistered.
   */
  void close();
}

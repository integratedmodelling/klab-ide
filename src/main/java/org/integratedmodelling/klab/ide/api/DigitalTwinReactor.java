package org.integratedmodelling.klab.ide.api;

import org.integratedmodelling.klab.ide.IDEContextScope;

/**
 * A DT reactor is a component that reacts to digital twins. As such it gets informed of when a new
 * one is created (either locally or within a federation) or set into focus through a UI action. A
 * new digital twin may be in focus in the UI or not.
 */
public interface DigitalTwinReactor {

  /**
   * The response to this should determine whether other functions are called.
   *
   * @param scope
   * @return
   */
  boolean isAffectedBy(IDEContextScope scope);

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

  /**
   * Called only if the reactor is affected by this scope when the scope is closed.
   *
   * @param ideContextScope
   */
  void closeDigitalTwin(IDEContextScope ideContextScope);
}

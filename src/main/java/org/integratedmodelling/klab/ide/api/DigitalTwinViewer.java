package org.integratedmodelling.klab.ide.api;

import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Schedule;
import org.integratedmodelling.klab.ide.IDEContextScope;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A digital twin viewer is dedicated to a single digital twin and gets informed of any event that
 * comes from it. In a viewer, the {@link #setDigitalTwin(IDEContextScope, boolean)} method should
 * be called only once at construction, normally by a DigitalTwinReactor that owns it.
 */
public interface DigitalTwinViewer extends DigitalTwinReactor {

  void submissionStarted(Observation observation);

  /** Supplies the client-side task handle when this viewer owns the submitting scope. */
  default void submissionTask(CompletableFuture<Observation> task) {}

  void submissionAborted(Observation observation);

  void submissionFinished(Observation observation);

  void setContext(Observation observation);

  void setObserver(Observation observation);

  void knowledgeGraphModified();

  void scheduleModified(Schedule schedule);

  void cleanup();

  /**
   * The IDE scope will contain all the activities seen during contextualization, arranged
   * hierarchically. The hierarchy is based on transient information collected during resolution,
   * not related to the provenance graph structure (it must be reconstructed from the metadata if
   * restored from the knowledge graph).
   */
  void activitiesModified();

//  /**
//   * Communicate the IDs of all observations to be focused on from a recent successful commit to the
//   * knowledge graph. Any observation beyond the first is unlinked to the submitted observation, so
//   * it should be added to the graph for visibility.
//   *
//   * @param rootAsset
//   * @param focalAssets
//   */
//  void focusObservations(RuntimeAsset rootAsset, List<RuntimeAsset> focalAssets);
}

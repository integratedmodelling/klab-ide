package org.integratedmodelling.klab.ide.components;

import com.brunomnsilva.smartgraph.graphview.SmartGraphPanel;
import org.integratedmodelling.cli.Test;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.ide.Theme;

/** Painful asset wrapper, needed because SmartGraphFX isn't very flexible. */
public class Asset implements RuntimeAsset {

  RuntimeAsset delegate;

  public Asset(RuntimeAsset target) {
    this.delegate = target;
  }

  public RuntimeAsset getDelegate() {
    return delegate;
  }

  public void setDelegate(RuntimeAsset delegate) {
    this.delegate = delegate;
  }

  public long getId() {
    return delegate.getId();
  }

    @Override
    public long getTransientId() {
        return delegate.getTransientId();
    }

    public RuntimeAsset.Type classify() {
    return delegate.classify();
  }

  @Override
  public String toString() {
    return Theme.getLabel(delegate);
  }

  public void setStyle(SmartGraphPanel<RuntimeAsset, ClientKnowledgeGraph.Relationship> graphView) {

    var style =
        switch (classify()) {
          case OBSERVATION -> getObservationStyle((Observation) delegate);
          case ACTUATOR -> null;
          case CONTEXT -> "homeVertex";
          case DATAFLOW -> null;
          case PROVENANCE -> null;
          case ACTIVITY -> null;
          case PLAN -> null;
          case AGENT -> null;
          case ARTIFACT -> null;
          case DATA -> null;
          case LINK -> null;
        };
    var inlineStyle =
        switch (classify()) {
          case OBSERVATION -> null;
          case ACTUATOR -> null;
          case CONTEXT -> "-fx-fill: url(\"file:icons8-home-24.png\");";
          case DATAFLOW -> null;
          case PROVENANCE -> null;
          case ACTIVITY -> null;
          case PLAN -> null;
          case AGENT -> null;
          case ARTIFACT -> null;
          case DATA -> null;
          case LINK -> null;
        };
    if (style != null) {
      graphView.getStylableVertex(this).setStyleClass(style);
    }
    if (inlineStyle != null) {
      graphView.getStylableVertex(this).setStyleInline(inlineStyle);
    }
  }

  private String getObservationStyle(Observation observation) {
    if (observation.getObservable().is(SemanticType.QUALITY)) {
      return "qualityVertex";
    } else if (observation.getObservable().is(SemanticType.SUBJECT)
        || observation.getObservable().is(SemanticType.AGENT)) {
      return observation.getObservable().getSemantics().isCollective()
          ? "pluralSubjectVertex"
          : "subjectVertex";
    } else if (observation.getObservable().is(SemanticType.PROCESS)) {
    } else if (observation.getObservable().is(SemanticType.EVENT)) {
    } else if (observation.getObservable().is(SemanticType.RELATIONSHIP)) {
    }

    return null;
  }
}

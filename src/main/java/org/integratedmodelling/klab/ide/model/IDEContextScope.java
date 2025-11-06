package org.integratedmodelling.klab.ide.model;

import java.io.Serializable;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import javafx.application.Platform;
import org.integratedmodelling.common.services.client.digitaltwin.ClientDigitalTwin;
import org.integratedmodelling.common.services.client.scope.ClientContextScope;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Schedule;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.Report;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.ide.api.DigitalTwinViewer;
import org.integratedmodelling.klab.ide.components.DigitalTwinControlPanel;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/**
 * Future delegate with UI features to substitute IDEContextScope. All derivations return the
 * derived context but also inform any views of the changes. Listeners are installed to build
 * notifications and closing removes all views.
 */
public class IDEContextScope implements ContextScope {

  private ClientContextScope delegate;
  // the scope keeps a list of all the viewers dedicated to it.
  private final Set<DigitalTwinViewer> viewers = Collections.synchronizedSet(new HashSet<>());
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  private Graph<Activity, DefaultEdge> activityGraph =
      new DefaultDirectedGraph<>(DefaultEdge.class);
  private HashMap<Long, Activity> activities = new HashMap<>();
  private Schedule schedule;
  private final AtomicReference<List<RuntimeAsset>> focalObservations =
      new AtomicReference<>(List.of(RuntimeAsset.CONTEXT_ASSET));
  private List<RuntimeAsset> focalAssets = Collections.synchronizedList(new ArrayList<>());
  private int graphDepth = 2;

  public IDEContextScope(ClientContextScope delegate) {
    this.delegate = delegate;
    delegate
        .getDigitalTwin()
        .addEventConsumer(message -> executor.execute(() -> processEvent(message)));
  }

  public void removeViewer(DigitalTwinViewer viewer) {
    viewers.remove(viewer);
  }

  public void addViewer(DigitalTwinViewer viewer) {
    this.viewers.add(viewer);
  }

  public List<RuntimeAsset> getFocalAssets() {
    return focalAssets;
  }

  public void setFocalAssets(RuntimeAsset... assets) {
    focalAssets.clear();
    if (assets != null) {
      focalAssets.addAll(Arrays.asList(assets));
    }
    for (var view : viewers) {
      view.focusObservations(focalAssets);
    }
  }

  public int getGraphDepth() {
    return this.graphDepth;
  }

  public void setGraphDepth(int newDepth) {
    if (newDepth >= 1 && newDepth <= 5) {
      this.graphDepth = newDepth;
      for (var view : viewers) {
        view.focusObservations(focalAssets);
      }
    }
  }

  private void processEvent(Message message) {

    switch (message.getMessageType()) {
      //      case ObservationSubmissionAborted -> {}
      //      case ObservationSubmissionStarted -> {}
      case ObservationSubmissionFinished -> {
        var observation = message.getPayload(Observation.class);
        executor.execute(() -> viewers.forEach(v -> v.submissionFinished(observation)));
      }
      case ObservationsInFocus -> {
        var ids = message.getPayload(String.class);
        var observations =
            Utils.Data.parseList(ids, Long.class, ",").stream()
                .map(
                    id ->
                        delegate
                            .getDigitalTwin()
                            .getKnowledgeGraph()
                            .getAsset(id, delegate, RuntimeAsset.class))
                .toList();
        this.setFocus(observations);
      }
      case ActivityFinished -> {
        var activity = message.getPayload(Activity.class);
        // update the existing activity in the graph
        var existingActivity = activities.get(activity.getTransientId());
        if (existingActivity instanceof ActivityImpl impl) {
          impl.setEnd(activity.getEnd());
          impl.setOutcome(activity.getOutcome());
        }
        executor.execute(() -> viewers.forEach(v -> v.activitiesModified(activityGraph)));
      }
      case ActivityStarted -> {
        var activity = message.getPayload(Activity.class);
        if (!activities.containsKey(activity.getTransientId())) { // shouldn't be needed
          activities.put(activity.getTransientId(), activity);
          activityGraph.addVertex(activity);
          var parentActivity = activity.getParentTransientId();
          if (parentActivity > 0) {
            var activityParent = activities.get(parentActivity);
            if (activityParent != null) {
              activityGraph.addEdge(activityParent, activity);
            }
          }
        }
        executor.execute(() -> viewers.forEach(v -> v.activitiesModified(activityGraph)));
      }
      case ScheduleModified -> {
        this.schedule = message.getPayload(Schedule.class);
        executor.execute(() -> viewers.forEach(v -> v.scheduleModified(schedule)));
      }
    }
  }

  public void setFocus(List<RuntimeAsset> ids) {
    this.focalObservations.set(ids);
    executor.execute(() -> viewers.forEach(v -> v.focusObservations(ids)));
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public String getHostServiceId() {
    return delegate.getHostServiceId();
  }

  @Override
  public KActorsBehavior.Ref getAgent() {
    return delegate.getAgent();
  }

  @Override
  public <T extends Serializable> T ask(Class<T> resultClass, Object... messageArgs) {
    return delegate.ask(resultClass, messageArgs);
  }

  @Override
  public List<ContextScope> getActiveContexts() {
    return delegate.getActiveContexts();
  }

  @Override
  public ContextScope createContext(DigitalTwin.Configuration configuration) {
    return delegate.createContext(configuration);
  }

  @Override
  public Scope getParentScope() {
    return delegate.getParentScope();
  }

  @Override
  public <T extends Scope> T getParentScope(Type type, Class<T> scopeClass) {
    return delegate.getParentScope(type, scopeClass);
  }

  @Override
  public Parameters<String> getData() {
    return delegate.getData();
  }

  @Override
  public <T extends KlabService> T getService(Class<T> serviceClass, Predicate<T>... selectors) {
    return delegate.getService(serviceClass, selectors);
  }

  @Override
  public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
    return delegate.getServices(serviceClass);
  }

  @Override
  public Type getType() {
    return delegate.getType();
  }

  @Override
  public Status getStatus() {
    return delegate.getStatus();
  }

  @Override
  public void setStatus(Status status) {
    delegate.setStatus(status);
  }

  @Override
  public void setData(String key, Object value) {
    delegate.setData(key, value);
  }

  @Override
  public UserIdentity getUser() {
    return delegate.getUser();
  }

  @Override
  public ContextScope connect(URL digitalTwinURL) {
    return delegate.connect(digitalTwinURL);
  }

  @Override
  public ContextScope connect(DigitalTwin.Configuration configuration) {
    return delegate.connect(configuration);
  }

  @Override
  public List<SessionScope> getActiveSessions() {
    return delegate.getActiveSessions();
  }

  @Override
  public SessionScope getUserSession(RuntimeService hostService) {
    return delegate.getUserSession(hostService);
  }

  @Override
  public SessionScope run(String behaviorName, RuntimeService hostService) {
    return delegate.run(behaviorName, hostService);
  }

  @Override
  public URL getUrl() {
    return delegate.getUrl();
  }

  @Override
  public DigitalTwin.Transaction getCurrentTransaction() {
    return delegate.getCurrentTransaction();
  }

  @Override
  public Observation getObserver() {
    return delegate.getObserver();
  }

  @Override
  public List<Observation> getObservations() {
    return delegate.getObservations();
  }

  @Override
  public Observation getObservation(Observation observation) {
    return delegate.getObservation(observation);
  }

  @Override
  public <T extends Observation> Collection<T> getPerspectives(Observable observable) {
    return delegate.getPerspectives(observable);
  }

  @Override
  public Observation getObserverOf(Observation observation) {
    return delegate.getObserverOf(observation);
  }

  @Override
  public Data getData(Observation... observations) {
    return delegate.getData(observations);
  }

  @Override
  public Collection<Observation> getRootObservations() {
    return delegate.getRootObservations();
  }

  @Override
  public Observation getContextObservation() {
    return delegate.getContextObservation();
  }

  @Override
  public ContextScope withObserver(Observation observer) {
    this.delegate =
        (ClientContextScope)
            (observer == null ? delegate.getRootContextScope() : delegate.withObserver(observer));
    for (var view : viewers) {
      view.setObserver(observer);
    }
    return this.delegate;
  }

  @Override
  public ContextScope within(Observation contextObservation) {
    this.delegate =
        (ClientContextScope)
            (contextObservation == null
                ? delegate.getRootContextScope()
                : delegate.within(contextObservation));
    for (var view : viewers) {
      view.setContext(contextObservation);
    }
    return this.delegate;
  }

  @Override
  public ContextScope between(Observation source, Observation target) {
    this.delegate = (ClientContextScope) delegate.between(source, target);
    // TODO ??
    return this.delegate;
  }

  @Override
  public ClientDigitalTwin getDigitalTwin() {
    return delegate.getDigitalTwin();
  }

  @Override
  public ContextScope connect(ContextScope remoteContext) {
    return delegate.connect(remoteContext);
  }

  @Override
  public CompletableFuture<Observation> submit(Observation observation) {
    return delegate.submit(observation);
  }

  @Override
  public Provenance getProvenance() {
    return delegate.getProvenance();
  }

  @Override
  public Provenance getProvenanceOf(Observation observation) {
    return delegate.getProvenanceOf(observation);
  }

  @Override
  public Report getReport() {
    return delegate.getReport();
  }

  @Override
  public Dataflow getDataflow() {
    return delegate.getDataflow();
  }

  @Override
  public ContextScope getRootContextScope() {
    return delegate.getRootContextScope();
  }

  @Override
  public RuntimeAsset getParentOf(RuntimeAsset asset) {
    return delegate.getParentOf(asset);
  }

  @Override
  public Collection<RuntimeAsset> getChildrenOf(RuntimeAsset asset) {
    return delegate.getChildrenOf(asset);
  }

  @Override
  public Collection<RuntimeAsset> getOutgoingRelationshipsOf(RuntimeAsset asset) {
    return delegate.getOutgoingRelationshipsOf(asset);
  }

  @Override
  public Collection<RuntimeAsset> getIncomingRelationshipsOf(RuntimeAsset asset) {
    return delegate.getIncomingRelationshipsOf(asset);
  }

  @Override
  public ContextScope withResolutionConstraints(ResolutionConstraint... resolutionConstraints) {
    return delegate.withResolutionConstraints(resolutionConstraints);
  }

  @Override
  public List<ResolutionConstraint> getResolutionConstraints() {
    return delegate.getResolutionConstraints();
  }

  @Override
  public Data.ShardingStrategy getShardingStrategy(Observation observation) {
    return delegate.getShardingStrategy(observation);
  }

  @Override
  public <T> T getConstraint(ResolutionConstraint.Type type, Class<T> resultClass) {
    return delegate.getConstraint(type, resultClass);
  }

  @Override
  public <T> T getConstraint(ResolutionConstraint.Type type, T defaultValue) {
    return delegate.getConstraint(type, defaultValue);
  }

  @Override
  public <T> List<T> getConstraints(ResolutionConstraint.Type type, Class<T> resultClass) {
    return delegate.getConstraints(type, resultClass);
  }

  @Override
  public DigitalTwin.Configuration getConfiguration() {
    return delegate.getConfiguration();
  }

  @Override
  public String getName() {
    return delegate.getName();
  }

  @Override
  public boolean isInterrupted() {
    return delegate.isInterrupted();
  }

  @Override
  public boolean hasErrors() {
    return delegate.hasErrors();
  }

  @Override
  public Identity getIdentity() {
    return delegate.getIdentity();
  }

  @Override
  public String getDispatchId() {
    return delegate.getDispatchId();
  }

  @Override
  public void info(Object... info) {
    delegate.info(info);
  }

  @Override
  public void warn(Object... o) {
    delegate.warn(o);
  }

  @Override
  public void error(Object... o) {
    delegate.error(o);
  }

  @Override
  public void debug(Object... o) {
    delegate.debug(o);
  }

  @Override
  public void event(Message message) {
    delegate.event(message);
  }

  @Override
  public void ui(Message message) {
    delegate.ui(message);
  }

  @Override
  public String onMessage(BiConsumer<Channel, Message> consumer, Message.Queue... queues) {
    return delegate.onMessage(consumer, queues);
  }

  @Override
  public void unregisterMessageListener(String listenerId) {
    delegate.unregisterMessageListener(listenerId);
  }

  @Override
  public Message send(Object... message) {
    return delegate.send(message);
  }

  @Override
  public void close() {
    // TODO remove listeners, send close message to UIs
    var list = new ArrayList<>(viewers);
    for (var view : list) {
      view.close();
    }
    delegate.close();
  }

  @Override
  public void interrupt() {
    delegate.interrupt();
  }

  @Override
  public boolean hasMessaging() {
    return delegate.hasMessaging();
  }

  @Override
  public boolean isConnected() {
    return delegate.isConnected();
  }

  @Override
  public boolean isSender() {
    return delegate.isSender();
  }

  @Override
  public boolean isReceiver() {
    return delegate.isReceiver();
  }
}

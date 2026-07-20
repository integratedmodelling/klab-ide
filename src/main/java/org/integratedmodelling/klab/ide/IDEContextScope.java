package org.integratedmodelling.klab.ide;

import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.integratedmodelling.common.services.client.digitaltwin.ClientDigitalTwin;
import org.integratedmodelling.common.services.client.scope.ClientContextScope;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Worldview;
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
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.ide.api.DigitalTwinViewer;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/**
 * Future delegate with UI features to substitute IDEContextScope. All derivations return the
 * derived context but also inform any views of the changes. Listeners are installed to build
 * notifications and closing removes all views.
 *
 * <p>TODO the scope must be the single thing sending message to high-level UI components, which in
 * turn must inform their sub-viewers affected by that scope.
 */
public class IDEContextScope implements ContextScope {

  ClientContextScope delegate;
  // the scope keeps a list of all the viewers dedicated to it.
  private final Set<DigitalTwinViewer> viewers = Collections.synchronizedSet(new HashSet<>());
  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  private Graph<Activity, DefaultEdge> activityGraph =
      new DefaultDirectedGraph<>(DefaultEdge.class);
  private HashMap<Long, Activity> activities = new HashMap<>();
  private Schedule schedule;
  private AtomicReference<RuntimeAsset> focalRoot =
      new AtomicReference<>(RuntimeAsset.CONTEXT_ASSET);
  private final AtomicReference<RuntimeAsset> focalAsset = new AtomicReference<>(null);
  private int graphDepth = 2;
  private final List<Pair<KnowledgeGraph.Commit, Observation>> commits =
      Collections.synchronizedList(new ArrayList<>());

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

  public RuntimeAsset getFocalAsset() {
    return focalAsset.get();
  }

  public void setFocalAssets(RuntimeAsset rootAsset, RuntimeAsset focalAssets) {
    focalAsset.set(focalAssets);
    focalRoot.set(rootAsset);
  }

  public List<Pair<KnowledgeGraph.Commit, Observation>> getCommits() {
    return commits;
  }

  @Override
  public String toString() {
    return delegate.getName();
  }

  public int getGraphDepth() {
    return this.graphDepth;
  }

  public void setGraphDepth(int newDepth) {
    if (newDepth >= 1 && newDepth <= 4) {
      this.graphDepth = newDepth;
    }
  }

  private void processEvent(Message message) {

    switch (message.getMessageType()) {
      //      case ObservationSubmissionAborted -> {}
      //      case ObservationSubmissionStarted -> {}
      case ObservationSubmissionFinished -> {
        var observation = message.getPayload(Observation.class);
        var root =
            observation.getMetadata().containsKey(Metadata.IM_COMMIT)
                ? observation.getMetadata().get(Metadata.IM_COMMIT, KnowledgeGraph.Commit.class)
                : RuntimeAsset.CONTEXT_ASSET;
        if (root instanceof KnowledgeGraph.Commit commit) {
          addCommit(Pair.of(commit, observation));
        }
        this.setFocus(root, observation);
        notifyViewers(
            viewer -> {
              viewer.submissionFinished(observation);
              viewer.knowledgeGraphModified();
            });
      }
      case ActivityFinished -> {
        var activity = message.getPayload(Activity.class);
        // update the existing activity in the graph instead of substituting it
        var existingActivity = activities.get(activity.getTransientId());
        if (existingActivity instanceof ActivityImpl impl) {
          impl.setStackTrace(activity.getStackTrace());
          impl.setObservationUrn(activity.getObservationUrn());
          impl.setEnd(activity.getEnd());
          impl.setOutcome(activity.getOutcome());
          impl.getMetadata().putAll(activity.getMetadata());
        }
        executor.execute(() -> viewers.forEach(DigitalTwinViewer::activitiesModified));
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
        executor.execute(() -> viewers.forEach(DigitalTwinViewer::activitiesModified));
      }
      case ScheduleModified -> {
        this.schedule = message.getPayload(Schedule.class);
        executor.execute(() -> viewers.forEach(v -> v.scheduleModified(schedule)));
      }
    }
  }

  private void addCommit(Pair<KnowledgeGraph.Commit, Observation> of) {
    commits.add(of);
  }

  private void notifyViewers(java.util.function.Consumer<DigitalTwinViewer> notification) {
    synchronized (viewers) {
      viewers.forEach(notification);
    }
  }

  public Graph<Activity, DefaultEdge> getActivityGraph() {
    return activityGraph;
  }

  public void setFocus(RuntimeAsset root, RuntimeAsset focus) {
    this.focalAsset.set(focus);
    this.focalRoot.set(root);
    //    executor.execute(() -> viewers.forEach(v -> v.focusObservations(root, ids)));
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
  public Agent getAgent() {
    return delegate.getAgent();
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
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public Collection<Notification> getNotifications() {
    return delegate.getNotifications();
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
  public <T extends KlabService> T getService(Class<T> serviceClass) {
    return delegate.getService(serviceClass);
  }

  @Override
  public <T extends KlabService> Optional<T> findService(
      Class<T> serviceClass, Predicate<T> selector) {
    return delegate.findService(serviceClass, selector);
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
  public Worldview getWorldview() {
    return delegate.getWorldview();
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
  public SessionScope run(KActorsBehavior behavior) {
    return delegate.run(behavior);
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

  //  @Override
  //  public Collection<Observation> getRootObservations() {
  //    return delegate.getRootObservations();
  //  }

  @Override
  public Observation getContextObservation() {
    return delegate.getContextObservation();
  }

  @Override
  public Observation getSourceObservation() {
    return delegate.getSourceObservation();
  }

  @Override
  public Observation getTargetObservation() {
    return delegate.getTargetObservation();
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
    // FIXME for now we keep a single layer of inheritance. This may become a problem or not.
    this.delegate = (ClientContextScope) delegate.getRootContextScope();
    if (contextObservation != null) {
      delegate = (ClientContextScope) delegate.within(contextObservation);
    }
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
  public Observation.Builder observation(Observable observable) {
    return delegate.observation(observable);
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
  public String getTransactionId() {
    return delegate.getTransactionId();
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

    var list = new ArrayList<>(viewers);
    for (var view : list) {
      view.close();
    }
    KlabIDEController.instance().unregisterDigitalTwin(this);
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
  public Federation getFederation() {
    return delegate.getFederation();
  }

  @Override
  public boolean isReceiver() {
    return delegate.isReceiver();
  }

  /**
   * Return the current context path, starting below RuntimeAsset.CONTEXT_ASSET.
   *
   * @return
   */
  public List<Observation> getContextPath() {

    Set<Long> seen = new HashSet<>();
    List<Observation> path = new ArrayList<>();
    ContextScope ctx = delegate;
    while (ctx != null) {
      var current = ctx.getContextObservation();
      if (current != null && seen.add(current.getId())) {
        path.add(current);
      }
      ctx = ctx.getParentScope(Type.CONTEXT, ContextScope.class);
    }
    Collections.reverse(path);
    return path;
  }

  public RuntimeAsset getFocalRoot() {
    return focalRoot.get();
  }
}

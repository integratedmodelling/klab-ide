package org.integratedmodelling.klab.ide;

import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import javafx.application.Platform;
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
 * <p>The scope is the single event boundary for the UI. Both client digital-twin messages and
 * modeler callbacks enter its serial queue; registered high-level viewers then refresh their owned
 * sub-views.
 */
public class IDEContextScope implements ContextScope {

  ClientContextScope delegate;
  private final DigitalTwinEventRouter eventRouter = new DigitalTwinEventRouter();
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final AtomicBoolean closed = new AtomicBoolean();

  private Graph<Activity, DefaultEdge> activityGraph =
      new DefaultDirectedGraph<>(DefaultEdge.class);
  private HashMap<Long, Activity> activities = new HashMap<>();
  private volatile Schedule schedule;
  private AtomicReference<RuntimeAsset> focalRoot =
      new AtomicReference<>(RuntimeAsset.CONTEXT_ASSET);
  private final AtomicReference<RuntimeAsset> focalAsset = new AtomicReference<>(null);
  private int graphDepth = 2;
  private final List<Pair<KnowledgeGraph.Commit, Observation>> commits =
      Collections.synchronizedList(new ArrayList<>());
  private final SubmissionCompletionTracker submissionCompletions =
      new SubmissionCompletionTracker();

  public IDEContextScope(ClientContextScope delegate) {
    this.delegate = Objects.requireNonNull(delegate);
    delegate
        .getDigitalTwin()
        .addEventConsumer(this::acceptDigitalTwinEvent);
  }

  public void removeViewer(DigitalTwinViewer viewer) {
    eventRouter.remove(viewer);
  }

  public void addViewer(DigitalTwinViewer viewer) {
    if (!closed.get()) {
      eventRouter.add(viewer);
    }
  }

  public RuntimeAsset getFocalAsset() {
    return focalAsset.get();
  }

  public void setFocalAssets(RuntimeAsset rootAsset, RuntimeAsset focalAssets) {
    focalAsset.set(isKnowledgeGraphAsset(focalAssets) ? focalAssets : null);
    focalRoot.set(
        isKnowledgeGraphAsset(rootAsset) ? rootAsset : RuntimeAsset.CONTEXT_ASSET);
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

  /**
   * Route a message that has already been ingested by the client digital twin. The client updates
   * its knowledge graph before invoking this method, so viewer refreshes always see the new graph.
   */
  public void acceptDigitalTwinEvent(Message message) {
    if (message != null) {
      executeEvent(() -> processEvent(message));
    }
  }

  public void notifySubmissionStarted(Observation observation) {
    executeEvent(() -> notifyViewers(viewer -> viewer.submissionStarted(observation)));
  }

  public void notifySubmissionAborted(Observation observation) {
    executeEvent(() -> notifyViewers(viewer -> viewer.submissionAborted(observation)));
  }

  public void notifySubmissionFinished(Observation observation) {
    executeEvent(() -> processSubmissionFinished(observation, false));
  }

  public void notifyContextChanged(Observation observation) {
    executeEvent(() -> notifyViewers(viewer -> viewer.setContext(observation)));
  }

  public void notifyObserverChanged(Observation observation) {
    executeEvent(() -> notifyViewers(viewer -> viewer.setObserver(observation)));
  }

  public void notifyKnowledgeGraphModified() {
    executeEvent(() -> notifyViewers(DigitalTwinViewer::knowledgeGraphModified));
  }

  private void executeEvent(Runnable event) {
    if (closed.get()) {
      return;
    }
    try {
      executor.execute(
          () -> {
            if (!closed.get()) {
              event.run();
            }
          });
    } catch (RejectedExecutionException ignored) {
      // Closing races with late messages from the client digital twin. They are intentionally
      // discarded once the scope has begun closing.
    }
  }

  private void processEvent(Message message) {

    switch (message.getMessageType()) {
      case ObservationSubmissionStarted ->
          notifyViewers(
              viewer -> viewer.submissionStarted(message.getPayload(Observation.class)));
      case ObservationSubmissionAborted ->
          notifyViewers(
              viewer -> viewer.submissionAborted(message.getPayload(Observation.class)));
      case ObservationSubmissionFinished ->
          processSubmissionFinished(message.getPayload(Observation.class), true);
      case ContextObservationResolved ->
          notifyViewers(viewer -> viewer.setContext(message.getPayload(Observation.class)));
      case ObserverResolved ->
          notifyViewers(viewer -> viewer.setObserver(message.getPayload(Observation.class)));
      case ActivityFinished -> {
        upsertActivity(message.getPayload(Activity.class), true);
        notifyViewers(DigitalTwinViewer::activitiesModified);
      }
      case ActivityStarted -> {
        upsertActivity(message.getPayload(Activity.class), false);
        notifyViewers(DigitalTwinViewer::activitiesModified);
      }
      case ScheduleModified -> {
        this.schedule = message.getPayload(Schedule.class);
        notifyViewers(viewer -> viewer.scheduleModified(schedule));
      }
      case ContextClosed, DigitalTwinDeleted -> Platform.runLater(this::close);
    }
  }

  private void upsertActivity(Activity activity, boolean finished) {
    var stored = activities.get(activity.getTransientId());
    if (stored == null) {
      stored = activity;
      activities.put(activity.getTransientId(), stored);
      activityGraph.addVertex(stored);
    } else if (finished && stored instanceof ActivityImpl impl) {
      impl.setStackTrace(activity.getStackTrace());
      impl.setObservationUrn(activity.getObservationUrn());
      impl.setEnd(activity.getEnd());
      impl.setOutcome(activity.getOutcome());
      impl.getMetadata().putAll(activity.getMetadata());
    }

    var parent = activities.get(stored.getParentTransientId());
    if (parent != null && !activityGraph.containsEdge(parent, stored)) {
      activityGraph.addEdge(parent, stored);
    }
    for (var possibleChild : activities.values()) {
      if (possibleChild.getParentTransientId() == stored.getTransientId()
          && !activityGraph.containsEdge(stored, possibleChild)) {
        activityGraph.addEdge(stored, possibleChild);
      }
    }
  }

  private void processSubmissionFinished(Observation observation, boolean knowledgeGraphCurrent) {
    if (!isKnowledgeGraphAsset(observation) || observation.isEmpty()) {
      notifyViewers(viewer -> viewer.submissionAborted(observation));
      return;
    }
    var completion = submissionCompletions.record(observation.getId(), knowledgeGraphCurrent);
    if (completion == SubmissionCompletionTracker.Decision.REFRESH_ONLY) {
      // The modeler callback may precede ingestion by ClientKnowledgeGraph. The graph-backed
      // duplicate is therefore not redundant: it is the first point at which viewers are
      // guaranteed to rebuild against the committed graph.
      notifyViewers(DigitalTwinViewer::knowledgeGraphModified);
      return;
    } else if (completion == SubmissionCompletionTracker.Decision.IGNORE) {
      return;
    }
    var root =
        observation.getMetadata().containsKey(Metadata.IM_COMMIT)
            ? observation.getMetadata().get(Metadata.IM_COMMIT, KnowledgeGraph.Commit.class)
            : RuntimeAsset.CONTEXT_ASSET;
    if (root instanceof KnowledgeGraph.Commit commit) {
      addCommit(Pair.of(commit, observation));
    }
    setFocus(root, observation);
    notifyViewers(
        viewer -> {
          viewer.submissionFinished(observation);
          viewer.knowledgeGraphModified();
        });
  }

  private void addCommit(Pair<KnowledgeGraph.Commit, Observation> of) {
    synchronized (commits) {
      if (commits.stream().noneMatch(commit -> commit.getFirst().getId() == of.getFirst().getId())) {
        commits.add(of);
      }
    }
  }

  private void notifyViewers(java.util.function.Consumer<DigitalTwinViewer> notification) {
    eventRouter.dispatch(notification);
  }

  public Graph<Activity, DefaultEdge> getActivityGraph() {
    return activityGraph;
  }

  public Schedule getSchedule() {
    return schedule;
  }

  public void setFocus(RuntimeAsset root, RuntimeAsset focus) {
    setFocalAssets(root, focus);
    //    executor.execute(() -> viewers.forEach(v -> v.focusObservations(root, ids)));
  }

  static boolean isKnowledgeGraphAsset(RuntimeAsset asset) {
    if (asset == null) {
      return false;
    }
    long id = asset.getId();
    return id > 0
        || id == RuntimeAsset.CONTEXT_ASSET_ID
        || id == RuntimeAsset.PROVENANCE_ASSET_ID
        || id == RuntimeAsset.DATAFLOW_ASSET_ID;
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
  public SessionScope run(KActorsBehavior behavior, RuntimeService service) {
    return delegate.run(behavior, service);
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
    notifyViewers(view -> view.setObserver(observer));
    return this;
  }

  @Override
  public ContextScope within(Observation contextObservation) {
    // FIXME for now we keep a single layer of inheritance. This may become a problem or not.
    this.delegate = (ClientContextScope) delegate.getRootContextScope();
    if (contextObservation != null) {
      delegate = (ClientContextScope) delegate.within(contextObservation);
    }
    notifyViewers(view -> view.setContext(contextObservation));
    return this;
  }

  @Override
  public ContextScope between(Observation source, Observation target) {
    this.delegate = (ClientContextScope) delegate.between(source, target);
    return this;
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
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    for (var view : eventRouter.drain()) {
      view.close();
    }
    executor.shutdownNow();
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

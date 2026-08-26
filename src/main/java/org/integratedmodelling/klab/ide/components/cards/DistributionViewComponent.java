package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Card;
import atlantafx.base.theme.Styles;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.Stack;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.DownloadMonitor;
import org.integratedmodelling.klab.ide.components.generic.CarouselBox;
import org.integratedmodelling.klab.ide.components.generic.IconButton;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.ide.components.generic.WaitButton;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.evaicons.Evaicons;
import org.kordamp.ikonli.materialdesign.MaterialDesign;

/** Installs, checks, removes, and selects binary k.LAB software-stack distributions. */
public class DistributionViewComponent extends BaseAssetViewComponent {

  private final Stack.Tag requestedTag;
  private final boolean synchronizeOnShow;
  private final Runnable synchronizationFinished;
  private final AtomicBoolean operationRunning = new AtomicBoolean();
  private final CarouselBox cards = new CarouselBox(Orientation.HORIZONTAL);
  private final DownloadMonitor progress = new DownloadMonitor();
  private final Runnable stateListener = this::refreshCards;

  public DistributionViewComponent() {
    this(null, false, null);
  }

  /** Create a single-distribution component suitable for the startup modal. */
  public DistributionViewComponent(
      Stack.Tag requestedTag, boolean synchronizeOnShow, Runnable synchronizationFinished) {
    super(AssetViewComponent.Type.Distribution, "Software stack", false);
    this.requestedTag = requestedTag;
    this.synchronizeOnShow = synchronizeOnShow;
    this.synchronizationFinished = synchronizationFinished;
    createContent();
    KlabIDEController.instance().addSoftwareStackStateListener(stateListener);
    if (synchronizeOnShow) {
      Platform.runLater(
          () -> {
            var tag = resolveRequestedTag();
            if (tag != null && tag.version() != Version.HEAD) {
              synchronize(tag);
            }
          });
    }
  }

  @Override
  public String getDescription() {
    return "Install, update and select software stack distributions";
  }

  @Override
  public Ikon getIcon() {
    return MaterialDesign.MDI_PACKAGE_VARIANT;
  }

  @Override
  protected Node createContent() {
    getChildren().clear();
    setSpacing(12);
    setPadding(new Insets(4));

    var heading = new Label("Available distributions");
    heading.getStyleClass().add(Styles.TITLE_3);
    var explanation =
        new Label(
            "Synchronize a binary stack, verify its local files, or choose which installed build the IDE should use.");
    explanation.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
    explanation.setWrapText(true);

    //    cards.setAlignment(Pos.TOP_LEFT);
    cards.setMaxWidth(Double.MAX_VALUE);
    progress.setVisible(false);
    progress.setManaged(false);
    getChildren().addAll(heading, explanation, progress, cards);
    refreshCards();
    return this;
  }

  private void refreshCards() {
    runOnFx(
        () -> {
          cards /*.getChildren()*/.clear();
          var stack = KlabIDEController.instance().engine().getSoftwareStack();
          if (stack == null) {
            cards./*getChildren().*/ addItem(
                new Label("Software stack metadata is not available."));
            return;
          }
          List<Stack.Tag> tags;
          if (requestedTag == null) {
            tags = stack.tags();
          } else {
            var resolved = stack.resolve(requestedTag);
            tags = resolved == null ? List.of() : List.of(resolved);
          }
          if (tags.isEmpty()) {
            cards./*getChildren().*/ addItem(
                new Label("No compatible software distributions were found."));
          } else {
            tags.forEach(tag -> cards./*getChildren().*/ addItem(makeCard(tag)));
          }
        });
  }

  private Card makeCard(Stack.Tag tag) {
    var controller = KlabIDEController.instance();
    var stack = controller.engine().getSoftwareStack();
    var current = Objects.equals(stack.resolve(controller.engine().getDistributionTag()), tag);
    var head = tag.version() == Version.HEAD;
    var mutable = controller.canRequestSoftwareStackChange();
    var busy = operationRunning.get();
    var pending = controller.isSoftwareStackChangePending(tag);

    var availabilityDot = new Label();
    availabilityDot.setMinSize(10, 10);
    availabilityDot.setMaxSize(10, 10);
    availabilityDot.setStyle(
        "-fx-background-radius: 5; -fx-background-color: "
            + (tag.orphan()
                ? "-color-danger-emphasis"
                : tag.availableLocally() ? "-color-success-emphasis" : "-color-fg-muted")
            + ";");

    var title = new Label(releaseName(tag));
    title.getStyleClass().add(Styles.TEXT_BOLD);
    var version = new Label(head ? "Source checkout" : "Version " + tag.version());
    version.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
    var titleBox = new VBox(1, title, version);
    HBox.setHgrow(titleBox, Priority.ALWAYS);

    Node currentControl;
    if (pending) {
      var switching = new WaitButton("Switching...");
      switching.getStyleClass().add(Styles.ACCENT);
      switching.showWaiting();
      currentControl = switching;
    } else if (current) {
      var currentLabel = new Label("Current");
      currentLabel.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_BOLD, Styles.SUCCESS);
      currentLabel.setGraphic(new IconLabel(Evaicons.CHECKMARK_CIRCLE_2, 14, Color.DARKGREEN));
      currentLabel.setTooltip(tooltip("This is the distribution used by the IDE"));
      currentControl = currentLabel;
    } else {
      var makeCurrent = new WaitButton("Make current");
      makeCurrent.getStyleClass().addAll(Styles.SMALL, Styles.ACCENT);
      makeCurrent.setDisable(
          !tag.availableLocally() || !mutable || busy || controller.isSoftwareStackChangePending());
      makeCurrent.setTooltip(
          tooltip(
              !tag.availableLocally()
                  ? "Synchronize the distribution before selecting it"
                  : !mutable
                      ? "Stop all local k.LAB and auxiliary services before switching"
                      : head
                          ? "Use this source checkout as the current IDE distribution"
                          : controller.canChangeSoftwareStack()
                              ? "Make this the current IDE distribution"
                              : "Make current and restart the language server"));
      makeCurrent.setOnAction(
          () -> {
            // switchDistributionTag may wait for a local language server to stop. WaitButton runs
            // this supplier away from the FX thread, after entering WAITING synchronously, so the
            // initial click always produces immediate visible feedback.
            var accepted = controller.switchDistributionTag(tag);
            runOnFx(
                () -> {
                  if (accepted) {
                    notifySuccess("Switching to " + displayName(tag));
                  }
                  // Rebuild from authoritative controller state. A pending switch gets the
                  // persistent "Switching..." spinner; a completed switch gets the Current label.
                  // This also removes the transient WaitButton before it can show a terminal icon.
                  refreshCards();
                });
            return accepted;
          });
      currentControl = makeCurrent;
    }

    var header = new HBox(8, availabilityDot, titleBox, currentControl);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setPadding(new Insets(10, 12, 6, 12));

    var build = new Label("Build " + formatBuild(tag.build()));
    build.getStyleClass().addAll(Styles.TEXT_SMALL);
    var state =
        new Label(
            head
                ? "Managed from the local source tree"
                : tag.orphan()
                    ? "Installed locally; no longer present in the network catalog"
                    : tag.availableLocally()
                        ? "Installed on this computer"
                        : "Available from the distribution network");
    state.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
    state.setWrapText(true);

    var actions = new HBox(6);
    actions.setAlignment(Pos.CENTER_LEFT);
    if (head) {
      var sourceNotice = new Label("Binary distribution actions do not apply to HEAD.");
      sourceNotice.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
      actions.getChildren().add(sourceNotice);
    } else {
      var synchronize =
          actionButton(
              Evaicons.DOWNLOAD,
              Color.DARKGREEN,
              "Synchronize",
              !busy && !tag.availableLocally(),
              () -> synchronize(tag));
      var verify =
          actionButton(
              Evaicons.FLAG,
              Color.DARKGOLDENROD,
              "Verify integrity",
              !busy && tag.availableLocally(),
              () -> verify(tag));
      var delete =
          actionButton(
              Evaicons.TRASH,
              Color.DARKRED,
              "Delete from disk",
              !busy && tag.availableLocally() && !current,
              () -> confirmDelete(tag));
      actions.getChildren().addAll(synchronize, verify, delete);
    }

    var body = new VBox(8, build, state, actions);
    body.setPadding(new Insets(4, 12, 10, 12));
    body.setMinHeight(100);

    var products = productSummary(tag);
    var footer = new HBox(6, new IconLabel(Theme.DEFINITION_ICON, 12, Color.GREY), products);
    footer.setAlignment(Pos.CENTER_LEFT);
    footer.setPadding(new Insets(6, 12, 9, 12));

    var card = new Card();
    card.setHeader(header);
    card.setBody(body);
    card.setFooter(footer);
    card.setPrefWidth(350);
    card.setMinWidth(320);
    return card;
  }

  private Label productSummary(Stack.Tag tag) {
    var build = KlabIDEController.instance().engine().getSoftwareStack().build(tag);
    var text =
        build == null
            ? "Product metadata unavailable"
            : build.getProducts().size() + " stack products";
    var label = new Label(text);
    label.getStyleClass().addAll(Styles.TEXT_SMALL, Styles.TEXT_MUTED);
    return label;
  }

  private IconButton actionButton(
      Ikon icon, Color color, String tooltip, boolean enabled, Runnable action) {
    return new IconButton(icon, 20, color, color, false) {
      @Override
      protected void action() {
        action.run();
      }
    }.enabled(enabled).styleClass(Styles.ROUNDED).tooltip(tooltip);
  }

  private void synchronize(Stack.Tag tag) {
    var controller = KlabIDEController.instance();
    var stack = controller.engine().getSoftwareStack();
    var current = Objects.equals(stack.resolve(controller.engine().getDistributionTag()), tag);
    if (current && !controller.canChangeSoftwareStack()) {
      notifyFailure("Stop local services before synchronizing the current distribution");
      return;
    }
    if (!operationRunning.compareAndSet(false, true)) {
      return;
    }
    showProgress("Preparing synchronization for " + displayName(tag));
    refreshCards();
    Thread.ofVirtual()
        .name("klab-distribution-sync")
        .start(
            () -> {
              boolean success = false;
              try {
                success =
                    KlabIDEController.instance()
                        .engine()
                        .getSoftwareStack()
                        .synchronize(tag, progress.synchronization());
                if (success) {
                  progress.complete("Synchronization completed");
                  notifySuccess(displayName(tag) + " synchronized successfully");
                } else {
                  progress.fail("Synchronization failed");
                  notifyFailure("Could not synchronize " + displayName(tag));
                }
              } catch (Throwable throwable) {
                Logging.INSTANCE.error(throwable);
                progress.fail("Synchronization failed: " + safeMessage(throwable));
                notifyFailure("Could not synchronize " + displayName(tag));
              } finally {
                operationRunning.set(false);
                runOnFx(
                    () -> {
                      if (synchronizeOnShow) {
                        KlabIDEController.instance().initializeSoftwareStack();
                      } else {
                        KlabIDEController.instance().refreshSoftwareStack();
                      }
                      hideProgress();
                      refreshCards();
                    });
                if (synchronizationFinished != null) {
                  KlabIDEController.instance().removeSoftwareStackStateListener(stateListener);
                  runOnFx(synchronizationFinished);
                }
              }
            });
  }

  private void verify(Stack.Tag tag) {
    if (!operationRunning.compareAndSet(false, true)) {
      return;
    }
    showProgress("Verifying " + displayName(tag));
    refreshCards();
    Thread.ofVirtual()
        .name("klab-distribution-verify")
        .start(
            () -> {
              try {
                var valid =
                    KlabIDEController.instance()
                        .engine()
                        .getSoftwareStack()
                        .verify(tag, progress.verification());
                if (valid) {
                  progress.complete("Verification completed: all files are valid");
                  notifySuccess(displayName(tag) + " passed integrity verification");
                } else {
                  progress.fail("Verification failed: synchronize to repair the distribution");
                  notifyFailure(displayName(tag) + " failed integrity verification");
                }
              } catch (Throwable throwable) {
                Logging.INSTANCE.error(throwable);
                progress.fail("Verification failed: " + safeMessage(throwable));
              } finally {
                operationRunning.set(false);
                runOnFx(
                    () -> {
                      hideProgress();
                      refreshCards();
                    });
              }
            });
  }

  private void confirmDelete(Stack.Tag tag) {
    var alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle("Delete software distribution");
    alert.setHeaderText("Delete " + displayName(tag) + " from this computer?");
    alert.setContentText(
        tag.orphan()
            ? "The distribution is no longer available online and will be lost."
            : "The distribution can be downloaded again later.");
    if (alert.showAndWait().filter(ButtonType.OK::equals).isPresent()) {
      delete(tag);
    }
  }

  private void delete(Stack.Tag tag) {
    var controller = KlabIDEController.instance();
    var stack = controller.engine().getSoftwareStack();
    if (!tag.availableLocally()
        || Objects.equals(stack.resolve(controller.engine().getDistributionTag()), tag)) {
      notifyFailure("Select another distribution before deleting this one");
      return;
    }
    if (!operationRunning.compareAndSet(false, true)) {
      return;
    }
    refreshCards();
    Thread.ofVirtual()
        .name("klab-distribution-delete")
        .start(
            () -> {
              try {
                if (KlabIDEController.instance().engine().getSoftwareStack().delete(tag)) {
                  notifySuccess(displayName(tag) + " was deleted from disk");
                } else {
                  notifyFailure("Could not delete " + displayName(tag));
                }
              } finally {
                operationRunning.set(false);
                runOnFx(
                    () -> {
                      KlabIDEController.instance().refreshSoftwareStack();
                      refreshCards();
                    });
              }
            });
  }

  private void showProgress(String operation) {
    runOnFx(
        () -> {
          progress.setManaged(true);
          progress.setVisible(true);
          progress.prepare(operation);
        });
  }

  private void hideProgress() {
    progress.setVisible(false);
    progress.setManaged(false);
  }

  private Stack.Tag resolveRequestedTag() {
    var stack = KlabIDEController.instance().engine().getSoftwareStack();
    return stack == null ? null : stack.resolve(requestedTag);
  }

  private static String releaseName(Stack.Tag tag) {
    var release = tag.release();
    if (release == null || release.isBlank() || "master".equalsIgnoreCase(release)) {
      return "Stable release" + (tag.orphan() ? " (orphaned)" : "");
    }
    return release.substring(0, 1).toUpperCase()
        + release.substring(1)
        + " release"
        + (tag.orphan() ? " (orphaned)" : "");
  }

  private static String displayName(Stack.Tag tag) {
    return releaseName(tag) + " " + tag.version() + " build " + tag.build();
  }

  private static String formatBuild(String build) {
    if (build != null && build.matches("\\d{12,}")) {
      return build.substring(6, 8)
          + "/"
          + build.substring(4, 6)
          + "/"
          + build.substring(0, 4)
          + " "
          + build.substring(8, 10)
          + ":"
          + build.substring(10, 12);
    }
    return build == null || build.isBlank() ? "unknown" : build;
  }

  private static Tooltip tooltip(String text) {
    var tooltip = new Tooltip(text);
    tooltip.setShowDelay(javafx.util.Duration.millis(150));
    return tooltip;
  }

  private static String safeMessage(Throwable throwable) {
    return throwable.getMessage() == null
        ? throwable.getClass().getSimpleName()
        : throwable.getMessage();
  }

  private static void notifySuccess(String message) {
    KlabIDEController.instance()
        .handleNotification(Notification.info(message, Notification.Outcome.Success));
  }

  private static void notifyFailure(String message) {
    KlabIDEController.instance()
        .handleNotification(Notification.warning(message, Notification.Outcome.Failure));
  }

  private static void runOnFx(Runnable runnable) {
    if (Platform.isFxApplicationThread()) {
      runnable.run();
    } else {
      Platform.runLater(runnable);
    }
  }
}

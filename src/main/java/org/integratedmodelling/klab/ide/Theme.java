package org.integratedmodelling.klab.ide;

import atlantafx.base.theme.*;
import atlantafx.base.util.BBCodeParser;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import org.atteo.evo.inflector.English;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.branding.Branding;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.lang.kim.KimModel;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.lang.kim.KimSymbolDefinition;
import org.integratedmodelling.klab.api.lang.kim.style.KimStyle;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableFolder;
import org.integratedmodelling.klab.ide.components.Asset;
import org.integratedmodelling.klab.ide.components.cards.ActivityCard;
import org.integratedmodelling.klab.ide.components.cards.ObservableCard;
import org.integratedmodelling.klab.ide.components.cards.ObservationCard;
import org.integratedmodelling.klab.ide.components.generic.BBCodeRenderer;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.integratedmodelling.klab.modeler.model.NavigableKimConceptStatement;
import org.integratedmodelling.klab.modeler.model.NavigableKimNamespace;
import org.integratedmodelling.klab.modeler.model.NavigableKimOntology;
import org.integratedmodelling.klab.modeler.model.NavigableProject;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.carbonicons.CarbonIcons;
import org.kordamp.ikonli.evaicons.Evaicons;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;
import org.kordamp.ikonli.materialdesign.MaterialDesign;
import org.kordamp.ikonli.unicons.UniconsLine;

public enum Theme {
  LIGHT_DEFAULT(false),
  DARK_DEFAULT(true),
  LIGHT_COOL(false),
  DARK_COOL(true),
  DARK_ALTERNATIVE(true);

  public enum Detail {
    /** Must fit on one line or equivalent. */
    ONE_LINER,
    /** Suitable for a tooltip or a carousel */
    BADGE,
    /**
     * Suitable for a complete description in the inspector or in a notebook item, potentially with
     * internal "link" buttons to substitute the contents.
     */
    CARD
  }

  private boolean dark;

  Theme(boolean dark) {
    this.dark = dark;
  }

  public static void setLabel(Label label, RuntimeAsset asset) {

    Platform.runLater(
        () -> {
          switch (asset) {
            case Observation observation -> {
              label.setText(
                  observation.getName() == null
                      ? observation.getObservable().getName()
                      : observation.getName());
              label.setGraphic(getGraphics(asset));
              label.setTooltip(new Tooltip(observation.getObservable().getUrn()));
            }
            default -> label.setText("?????");
          }
        });
  }

  public boolean isDark() {
    return dark;
  }

  public static Theme CURRENT_THEME = LIGHT_DEFAULT;

  // color coding for services. TODO may be non-static, styled according to the current theme
  public static final Color REASONER_COLOR_MUTED = Color.web("#b3d1ff");
  public static final Color RESOURCES_COLOR_MUTED = Color.web("#c2f0c2");
  public static final Color RESOLVER_COLOR_MUTED = Color.web("#ffd9b3");
  public static final Color RUNTIME_COLOR_MUTED = Color.web("#ffb3b3");
  public static final Color REASONER_COLOR_ACTIVE = Color.web("#0052cc");
  public static final Color RESOURCES_COLOR_ACTIVE = Color.web("#29a329");
  public static final Color RESOLVER_COLOR_ACTIVE = Color.web("#cc6600");
  public static final Color RUNTIME_COLOR_ACTIVE = Color.web("#cc0000");
  public static final Color FOREGROUND_COLOR = CURRENT_THEME.dark ? Color.LIGHTGREY : Color.BLACK;

  public static final Color SUBJECT_COLOR =
      Color.rgb(
          Branding.COLOR_SUBJECT_RGB[0],
          Branding.COLOR_SUBJECT_RGB[1],
          Branding.COLOR_SUBJECT_RGB[2]);
  public static final Color QUALITY_COLOR =
      Color.rgb(
          Branding.COLOR_QUALITY_RGB[0],
          Branding.COLOR_QUALITY_RGB[1],
          Branding.COLOR_QUALITY_RGB[2]);
  public static final Color EVENT_COLOR =
      Color.rgb(
          Branding.COLOR_EVENT_RGB[0], Branding.COLOR_EVENT_RGB[1], Branding.COLOR_EVENT_RGB[2]);
  public static final Color PROCESS_COLOR =
      Color.rgb(
          Branding.COLOR_PROCESS_RGB[0],
          Branding.COLOR_PROCESS_RGB[1],
          Branding.COLOR_PROCESS_RGB[2]);
  public static final Color TRAIT_COLOR =
      Color.rgb(
          Branding.COLOR_TRAIT_RGB[0], Branding.COLOR_TRAIT_RGB[1], Branding.COLOR_TRAIT_RGB[2]);
  public static final Color CONFIGURATION_COLOR =
      Color.rgb(
          Branding.COLOR_CONFIGURATION_RGB[0],
          Branding.COLOR_CONFIGURATION_RGB[1],
          Branding.COLOR_CONFIGURATION_RGB[2]);
  public static final Color RELATIONSHIP_COLOR =
      Color.rgb(
          Branding.COLOR_RELATIONSHIP_RGB[0],
          Branding.COLOR_RELATIONSHIP_RGB[1],
          Branding.COLOR_RELATIONSHIP_RGB[2]);
  public static final Color DOMAIN_COLOR =
      Color.rgb(
          Branding.COLOR_DOMAIN_RGB[0], Branding.COLOR_DOMAIN_RGB[1], Branding.COLOR_DOMAIN_RGB[2]);
  public static final Color ROLE_COLOR =
      Color.rgb(Branding.COLOR_ROLE_RGB[0], Branding.COLOR_ROLE_RGB[1], Branding.COLOR_ROLE_RGB[2]);
  // assets
  public static Ikon PROJECT_ICON = Material2MZ.WORK_OUTLINE;
  public static Ikon ONTOLOGY_ICON = CarbonIcons.CONCEPT;
  public static Ikon NAMESPACE_ICON = Material2AL.DEVELOPER_BOARD;
  public static Ikon MODEL_ICON = Material2MZ.WORK_OUTLINE;
  public static Ikon CONCEPT_DEFINITION_ICON = BootstrapIcons.LIGHTBULB_FILL;
  public static Ikon OBSERVATION_FOLDER_ICON = Material2AL.ADJUST;
  public static Ikon OBSERVATION_ICON = Material2AL.FIBER_MANUAL_RECORD;
  public static Ikon OBSERVER_ICON = UniconsLine.HEAD_SIDE;
  public static Ikon LOGS_ICON = Evaicons.FILE_TEXT_OUTLINE;
  public static Ikon ACTIVITY_ICON = Evaicons.ACTIVITY;
  public static Ikon DEFINITION_ICON = Material2MZ.WORK_OUTLINE;
  public static Ikon BEHAVIOR_ICON = Material2MZ.WORK_OUTLINE;
  public static Ikon FOLDER_ICON = Material2AL.FOLDER_OPEN;
  public static Ikon COHORT_ICON = Material2AL.FOLDER_OPEN;
  public static Ikon TESTCASE_ICON = Material2MZ.WORK_OUTLINE;
  public static Ikon APP_ICON = Material2MZ.WORK_OUTLINE;
  public static Ikon COMPONENT_ICON = Material2MZ.WORK_OUTLINE;
  public static Ikon ACTION_ICON = Material2MZ.WORK_OUTLINE;
  public static Ikon STRATEGY_DOCUMENT_ICON = Material2MZ.WORK_OUTLINE;
  public static Ikon STRATEGY_ICON = Material2MZ.WORK_OUTLINE;
  public static Ikon WORKSPACE_ICON = Material2AL.APPS;
  public static Ikon WORKSPACE_SETTINGS_ICON = CarbonIcons.SETTINGS;
  public static Ikon SCENARIO_ICON = UniconsLine.SCENERY;
  public static Ikon UNKNOWN_ICON = BootstrapIcons.LIGHTBULB_OFF_FILL;
  public static Ikon KNOWLEDGE_GRAPH_ICON = BootstrapIcons.DIAGRAM_3;
  public static Ikon INSTANTIATOR_MODEL_ICON = FontAwesomeSolid.COGS;
  public static Ikon RESOLVER_MODEL_ICON = FontAwesomeSolid.COG;
  public static Ikon DATABASE_ICON = MaterialDesign.MDI_DATABASE;
  public static Ikon LANGUAGE_SERVER_ICON = CarbonIcons.LANGUAGE;
  public static Ikon MESSAGING_ICON = Evaicons.MESSAGE_SQUARE_OUTLINE;

  // views
  public static Ikon RESOURCES_ICON = FontAwesomeSolid.CUBES;
  public static Ikon WORKSPACES_ICON = Material2AL.APPS; // BootstrapIcons.BORDER_ALL;
  public static Ikon DIGITAL_TWINS_ICON = Material2AL.GRAPHIC_EQ;
  public static Ikon APPLICATION_VIEW_ICON = Material2AL.DIRECTIONS_RUN;
  public static Ikon WORLDVIEW_ICON = Evaicons.BULB_OUTLINE;
  public static Ikon INSPECTOR_ICON = Evaicons.EYE;

  // functionalities
  public static final Ikon ADD_ASSET_ICON = Material2AL.ADD_CIRCLE;
  public static final Ikon ADD_PROJECT_ICON = Evaicons.FOLDER_ADD_OUTLINE;
  public static final Ikon IMPORT_ASSET_ICON = Material2AL.IMPORT_EXPORT;
  public static final Ikon EDIT_ICON = Material2AL.EDIT;
  public static final Ikon COLLAPSE_ICON = Evaicons.COLLAPSE;
  public static final Ikon EXPAND_ICON = Evaicons.EXPAND;
  public static final Ikon OPEN_IN_BROWSER = Material2MZ.OPEN_IN_BROWSER;

  // services
  public static final Ikon LOCAL_SERVICE_ICON = Material2AL.DONUT_SMALL;
  public static final Ikon REMOTE_SERVICE_ICON_ONE = BootstrapIcons.CLOUD_FILL;
  public static final Ikon REMOTE_SERVICE_ICON_MANY = BootstrapIcons.CLOUDS;
  public static final Ikon LOCAL_AND_REMOTE_SERVICE_ICON = MaterialDesign.MDI_CLOUD_SYNC;

  public static Ikon getIcon(Notification.Level level) {
    return switch (level) {
      // TODO
      case Debug -> MODEL_ICON;
      case Info -> MODEL_ICON;
      case Warning -> MODEL_ICON;
      case Error -> MODEL_ICON;
      case SystemError -> MODEL_ICON;
    };
  }

  public static Ikon getIcon(KlabAsset.KnowledgeClass knowledgeClass) {
    return switch (knowledgeClass) {
      case CONCEPT -> CONCEPT_DEFINITION_ICON;
      case OBSERVABLE -> CONCEPT_DEFINITION_ICON;
      case MODEL -> MODEL_ICON;
      case DEFINITION -> CONCEPT_DEFINITION_ICON;
      case RESOURCE -> RESOURCES_ICON;
      case NAMESPACE -> NAMESPACE_ICON;
      case BEHAVIOR -> BEHAVIOR_ICON;
      case SCRIPT -> BEHAVIOR_ICON;
      case TESTCASE -> TESTCASE_ICON;
      case APPLICATION -> APPLICATION_VIEW_ICON;
      case ONTOLOGY -> ONTOLOGY_ICON;
      case OBSERVATION_STRATEGY -> ONTOLOGY_ICON;
      case OBSERVATION_STRATEGY_DOCUMENT -> ONTOLOGY_ICON;
      case COMPONENT -> ONTOLOGY_ICON;
      case PROJECT -> PROJECT_ICON;
      case WORLDVIEW -> WORLDVIEW_ICON;
      case WORKSPACE -> WORKSPACE_ICON;
      case CONCEPT_STATEMENT -> CONCEPT_DEFINITION_ICON;
      case SERVICE_IMPLEMENTATION -> COMPONENT_ICON;
      case OBSERVATION -> OBSERVATION_ICON;
      case INFORMATION -> null; // shouldn't happen
    };
  }

  public static IconLabel getGraphics(Object asset) {

    int errorCount = 0;
    int warningCount = 0;
    int infoCount = 0;
    RepositoryState.Status repositoryStatus = null;

    if (asset instanceof Asset runtimeAsset) {
      asset = runtimeAsset.getDelegate();
    }

    if (asset instanceof NavigableAsset navigableAsset) {
      errorCount =
          navigableAsset.localMetadata().get(NavigableAsset.ERROR_NOTIFICATION_COUNT_KEY, 0);
      warningCount =
          navigableAsset.localMetadata().get(NavigableAsset.WARNING_NOTIFICATION_COUNT_KEY, 0);
      infoCount = navigableAsset.localMetadata().get(NavigableAsset.INFO_NOTIFICATION_COUNT_KEY, 0);
      repositoryStatus =
          navigableAsset
              .localMetadata()
              .get(NavigableAsset.REPOSITORY_STATUS_KEY, RepositoryState.Status.class);
    }

    SemanticType semanticType = null;
    var icon =
        switch (asset) {
          case NavigableProject ignored -> PROJECT_ICON;
          case NavigableKimOntology ignored -> ONTOLOGY_ICON;
          case NavigableKimNamespace ignored -> NAMESPACE_ICON;
          case NavigableFolder ignored -> FOLDER_ICON;
          case Cohort ignored -> COHORT_ICON;
          case Observation observation -> {
            semanticType =
                SemanticType.fundamentalType(observation.getObservable().getSemantics().getType());
            yield observation.getObservable().getSemantics().isCollective()
                ? OBSERVATION_FOLDER_ICON
                : OBSERVATION_ICON;
          }
          case NavigableKimConceptStatement definition -> {
            semanticType = SemanticType.fundamentalType(definition.getType());
            yield semanticType == SemanticType.NOTHING ? UNKNOWN_ICON : CONCEPT_DEFINITION_ICON;
          }
          case KimSymbolDefinition definition -> {
            // TODO change based on class and, if applicable, define semantics
            yield OBSERVATION_ICON;
          }
          case KimModel model -> {
            semanticType =
                model.getObservables().isEmpty()
                    ? SemanticType.NOTHING
                    : SemanticType.fundamentalType(
                        model.getObservables().getFirst().getSemantics().getType());
            yield model.getObservables().isEmpty()
                ? RESOLVER_MODEL_ICON
                : (model.getObservables().getFirst().getSemantics().isCollective()
                    ? INSTANTIATOR_MODEL_ICON
                    : RESOLVER_MODEL_ICON);
          }
          // TODO all runtime asset first
          case RuntimeAsset ignored -> KNOWLEDGE_GRAPH_ICON;
          default -> UNKNOWN_ICON;
        };
    var color = CURRENT_THEME.getDefaultTextColor();
    if (errorCount > 0) {
      color = Color.RED;
    } else if (warningCount > 0) {
      color = Color.GOLDENROD;
    } else if (infoCount > 0) {
      color = Color.BLUE;
    }

    if (semanticType != null) {
      color = getColorForType(semanticType);
    }

    return new IconLabel(icon, 18, color);
  }

  public static Color getColorForType(SemanticType semanticType) {
    return switch (semanticType) {
      case SUBJECT, AGENT -> SUBJECT_COLOR;
      case QUALITY -> QUALITY_COLOR;
      case EVENT -> EVENT_COLOR;
      case PROCESS -> PROCESS_COLOR;
      case ROLE -> ROLE_COLOR;
      case IDENTITY, ATTRIBUTE, REALM -> TRAIT_COLOR;
      case CONFIGURATION -> CONFIGURATION_COLOR;
      case RELATIONSHIP -> RELATIONSHIP_COLOR;
      case DOMAIN -> DOMAIN_COLOR;
      case NOTHING -> Color.RED;
      default -> throw new KlabInternalErrorException("KAKA " + semanticType);
    };
  }

  public static <T> String getLabel(T asset) {

    if (asset instanceof NavigableAsset navigableAsset) {
      var repositoryStatus =
          navigableAsset
              .localMetadata()
              .get(NavigableAsset.REPOSITORY_STATUS_KEY, RepositoryState.Status.class);
      var ret = repositoryStatusPrefix(repositoryStatus) + navigableAsset.getUrn();
      if (asset instanceof Project) {
        var branch =
            navigableAsset
                .localMetadata()
                .get(NavigableAsset.REPOSITORY_CURRENT_BRANCH_KEY, String.class);
        if (branch != null) {
          ret += " [" + branch + "]";
        }
      }
      return ret;
    } else if (asset instanceof RuntimeAsset runtimeAsset) {
      // TODO all real chances first
      if (asset instanceof Observation observation) {
        return Branding.observationDescription(observation, Branding.DescriptionStyle.SHORTEST);
      } else if (asset instanceof Cohort cohort) {
        return English.plural(
            Branding.conceptDescription(
                cohort.getObservable(), Branding.DescriptionStyle.SHORTEST));
      } else if (asset instanceof RuntimeAsset.ContextAsset) {
        return "Knowledge Graph";
      } else if (asset instanceof KnowledgeGraph.Commit commit) {
        return "Commit "
            + Utils.Time.actualizedFormat(TimeInstant.create(commit.getTimestamp()))
            + " by "
            + commit.getOwner();
      } else if (asset instanceof Storage.Shard shard) {
        return "Data (" + shard.getGeometry().size() + ")";
      } else if (asset == RuntimeAsset.DATAFLOW_ASSET) {
        return "Dataflow";
      } else if (asset == RuntimeAsset.PROVENANCE_ASSET) {
        return "Provenance";
      } else if (asset instanceof Actuator) {
        return "Actuator"; // TODO
      } else if (asset instanceof Activity activity) {
        return Utils.Strings.capitalize(activity.getType().name().toLowerCase())
            + " activity"; // TODO improve
      }
      return "CARAJO " + asset;
    }

    return asset.toString();
  }

  private static String repositoryStatusPrefix(RepositoryState.Status repositoryStatus) {

    if (repositoryStatus == null) {
      return "";
    }

    return switch (repositoryStatus) {
      case UNTRACKED, ADDED -> "? ";
      case CONFLICTED -> "! ";
      case MODIFIED -> "> ";
      // TODO more
      default -> "";
    };
  }

  public String getStylesheet() {
    return switch (this) {
      case LIGHT_DEFAULT -> new PrimerLight().getUserAgentStylesheet();
      case DARK_DEFAULT -> new PrimerDark().getUserAgentStylesheet();
      case LIGHT_COOL -> new NordLight().getUserAgentStylesheet();
      case DARK_COOL -> new NordDark().getUserAgentStylesheet();
      case DARK_ALTERNATIVE -> new Dracula().getUserAgentStylesheet();
    };
  }

  public Color getDefaultTextColor() {
    return switch (this) {
      case LIGHT_DEFAULT -> Color.web("#24292FFF");
      case DARK_DEFAULT -> Color.web("#C9D1D9FF");
      case LIGHT_COOL -> Color.web("#2E3440FF");
      case DARK_COOL -> Color.web("#ECEFF4FF");
      case DARK_ALTERNATIVE -> Color.web("#F8F8F2FF");
    };
  }

  /**
   * Return a displayable object that describes the given object at the requested level of detail.
   * Should return a JavaFX Node. At worst, return a string description.
   *
   * @param object
   * @param detail
   * @return
   */
  public static Object getDisplayObject(Object object, Detail detail) {

    var scope = KlabIDEController.scope();
    if (object instanceof Semantics concept && scope != null) {
      if (detail == Detail.CARD) {
        return new ObservableCard(Observable.promote(concept), true);
      } else {
        var kim =
            scope
                .getService(ResourcesService.class)
                .retrieve(concept.getUrn(), KimObservable.class, scope);
        if (kim != null) {
          // TODO investigate caching, this stuff may be done often
          var formatted = kim.format(new KimStyle.KimStylingAppender(scope));
          if (formatted != null) {
            if (detail == Detail.ONE_LINER) {
              var txt = formatted.render(BBCodeRenderer.INSTANCE);
              var ret = BBCodeParser.createFormattedText(txt);
              ret.setMaxWidth(Region.USE_COMPUTED_SIZE);
              ret.setPrefHeight(32);
              ret.setTextAlignment(TextAlignment.LEFT);
              return ret;
            }
          }
        }
      }
    } else if (object instanceof Activity activity) {
      if (detail == Detail.CARD) {
        return new ActivityCard(activity, true);
      } else if (detail == Detail.BADGE) {
        return new ActivityCard(activity, false);
      }
    } else if (object instanceof Observation observation) {
      if (detail == Detail.CARD) {
        return new ObservationCard(observation, true);
      } else if (detail == Detail.BADGE) {
        return new ObservationCard(observation, false);
      }
    }

    return getLabel(object);
  }

  public static String getDescription(Observation asset) {
    // TODO temporary
    return "Description: " + getLabel(asset);
  }
}

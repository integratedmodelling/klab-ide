package org.integratedmodelling.klab.ide.components;

import atlantafx.base.controls.Card;
import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;
import org.integratedmodelling.klab.ide.KlabIDEApplication;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.Theme;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.material2.Material2MZ;

/**
 * Components are widgets that can fit into the {@link NotebookView}. They have a builder that
 * facilitates their construction and posting. They may have an ID and should save/restore their
 * state.
 */
public class Components {

  public enum Type {
    Distribution,
    Message,
    UserInfo,
    ServiceInfo,
    Help,
    About,
    Settings,
    //    AutoScroll, // Auto-scrolling component
    Object // these are not indexed and may be used outside the notebook
  }

  public interface Component {

    Type getType();

    String getTitle();
  }

  public abstract static class BaseComponent extends VBox implements Component {

    String title;
    Type type;

    public BaseComponent(Type type, String title, boolean initialize) {
      super(10);
      this.title = title;
      this.type = type;
      if (initialize) {
        createContent();
      }
    }

    @Override
    public Type getType() {
      return type;
    }

    @Override
    public String getTitle() {
      return title;
    }

    protected abstract void createContent();
  }

  public static class About extends BaseComponent {

    public About() {
      super(Type.About, "About k.LAB", true);
    }

    protected void createContent() {

      var card = new Card();
      VBox content = new VBox(20);
      content.setPadding(new Insets(20));

      try (var lg =
          this.getClass()
              .getResourceAsStream("/org/integratedmodelling/klab/ide/icons/klab-im.png")) {
        var logo = new Image(lg, 420, 180, true, true);
        HBox logoBox = new HBox(new ImageView(logo));
        logoBox.setAlignment(Pos.CENTER);

        TextArea description = new TextArea();
        description.setText(
            "k.LAB is a distributed semantic modeling platform enabling integration of diverse knowledge. "
                + "k.LAB aims to address integrated modeling, which reconciles strong "
                + "semantics with modeling practice, helping achieve advantages such as modularity, "
                + "interoperability, reusability, and integration of multiple paradigms and scales. ");
        description.setWrapText(true);
        description.setEditable(false);
        description.setPrefRowCount(5);

        HBox links = new HBox(5);
        links
            .getChildren()
            .addAll(
                createLink("Documentation", "https://docs.integratedmodelling.org"),
                createLink("Source Code", "https://github.com/integratedmodelling/klab-services"),
                createLink("Website", "https://www.integratedmodelling.org"),
                createLink("License", "https://www.gnu.org/licenses/agpl-3.0.en.html"));

        VBox rightContent = new VBox(10);
        rightContent.getChildren().addAll(description, links);

        HBox contentBox = new HBox(20, logoBox, rightContent);
        content.getChildren().add(contentBox);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      List<Node> developers = new ArrayList<>();
      for (var developer :
          List.of(
              "Ferdinando Villa, always",
              "Enrico Girotto, hopefully",
              "Andrea Antonello, maybe",
              "Iñigo Cobian, eventually",
              "Arnab Moitra, tangentially")) {
        Label label = new Label(developer);
        label.setAlignment(Pos.CENTER);
        label.setPadding(new Insets(10));
        label.setStyle("-fx-font-size: 10px;");
        developers.add(label);
      }

      // Create a horizontal auto-scroll pane
      AutoScrollPane devScroll = new AutoScrollPane(Orientation.HORIZONTAL, 50);
      devScroll.setPrefHeight(40);
      devScroll.setMaxWidth(Double.MAX_VALUE);
      HBox.setHgrow(devScroll, Priority.ALWAYS);
      devScroll.setComponents(developers);

      Label copyright =
          new Label(
              "Version "
                  + Version.CURRENT
                  + " :: © 2025 Integrated Modelling Partnership. All rights reserved. Main developers:");
      copyright.setStyle("-fx-font-size: 10px; -fx-padding: 10 0 0 0;");
      copyright.setAlignment(Pos.CENTER);
      copyright.setPrefWidth(480);

      var credits = new HBox(copyright, devScroll);
      credits.setSpacing(4);

      credits.setMaxWidth(Double.MAX_VALUE);
      HBox.setHgrow(credits, Priority.ALWAYS);

      content.getChildren().addAll(credits);

      card.setBody(content);
      this.getChildren().add(card);
    }

    private Node createLink(String text, String url) {
      Hyperlink link = new Hyperlink(text);
      FontIcon icon = new FontIcon(Material2AL.LINK);
      HBox linkBox = new HBox(5, icon, link);

      link.setOnAction(e -> KlabIDEApplication.instance().getHostServices().showDocument(url));
      return linkBox;
    }

    private Label createDeveloperLabel(String name) {
      Label label = new Label(name);
      label.setStyle(
          "-fx-padding: 5 10; -fx-background-color: #f0f0f0; -fx-background-radius: 15;");
      return label;
    }
  }

  public static class User extends BaseComponent {

    private UserScope user;
    private Label usernameLabel;
    private Label emailLabel;
    private Label licenseLabel;
    private Label statusLabel;
    private GridPane groupIcons;
    private VBox groupArea;

    public User(UserScope userScope) {
      super(Type.UserInfo, "User information", false);
      this.user = userScope;
      createContent();
    }

    protected void createContent() {

      var icon = new IconLabel(Material2MZ.PERSON, 32, Color.BLACK);

      usernameLabel = new Label(user.getUser().getUsername());
      usernameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

      emailLabel = new Label(user.getUser().getEmailAddress());
      emailLabel.setStyle("-fx-font-size: 14px;");

      licenseLabel = new Label("Licensed exclusively for not-for-profit use");
      licenseLabel.setStyle("-fx-font-size: 14px;");

      statusLabel = new Label(user.getUser().isOnline() ? "Online" : "Offline");
      statusLabel.setStyle(
          user.getUser().isOnline()
              ? "-fx-text-fill: green; -fx-font-weight: bold;"
              : "-fx-text-fill: red; -fx-font-weight: bold;");

      VBox userInfoArea = new VBox(5);
      userInfoArea.setAlignment(Pos.TOP_LEFT);
      userInfoArea
          .getChildren()
          .addAll(new HBox(10, icon, usernameLabel), emailLabel, licenseLabel, statusLabel);
      HBox.setHgrow(userInfoArea, Priority.ALWAYS);

      groupIcons = new GridPane();
      groupIcons.setHgap(5);
      groupIcons.setVgap(5);

      Label groupsLabel = new Label("Groups");
      groupsLabel.setStyle("-fx-font-weight: bold;");

      groupArea = new VBox(5);
      groupArea.getChildren().addAll(groupsLabel, groupIcons);

      int row = 0, col = 0;
      for (var group : user.getUser().getGroups()) {
        Label groupIcon =
            new Label(
                group.getName().substring(0, Math.min(2, group.getName().length())).toUpperCase());
        groupIcon.setStyle(
            "-fx-background-color: #e0e0e0; -fx-padding: 5 10; -fx-background-radius: 3;");
        Tooltip.install(groupIcon, new Tooltip(group.getName()));
        groupIcons.add(groupIcon, col, row);
        col++;
        if (col > 2) {
          col = 0;
          row++;
        }
      }

      VBox dropZone = new VBox();
      dropZone.setAlignment(Pos.CENTER);
      dropZone.setPrefWidth(200);
      dropZone.setPrefHeight(150);
      dropZone.setStyle(
          "-fx-border-color: #808080; -fx-border-width: 3; -fx-border-style: dashed; "
              + "-fx-border-radius: 10; -fx-background-color: #f8f8f8; -fx-background-radius: 10;");

      Label dropLabel = new Label("Drop a new certificate");
      dropLabel.setStyle("-fx-text-fill: #808080;");
      dropZone.getChildren().add(dropLabel);

      dropZone.setOnDragOver(
          event -> {
            event.acceptTransferModes(TransferMode.COPY);
            event.consume();
          });

      dropZone.setOnDragDropped(
          event -> {
            event.setDropCompleted(true);
            event.consume();
          });

      HBox main = new HBox(20, userInfoArea, groupArea, dropZone);
      main.setPadding(new Insets(10));
      var card = new Card();
      card.setBody(main);

      this.getChildren().add(card);
    }

    //    public void setupAuthenticationUI() {
    //      if (this.authentication != null) {
    //        switch(this.authentication.getStatus()) {
    //          case ANONYMOUS:
    //            certContentLabel.setText("No certificate");
    //            certContentLabel.setTextFill(Paint.valueOf(COLOR_RED));
    //            certUsername.setText("Anonymous");
    //            certUsername.setTextFill(Paint.valueOf(COLOR_LIGHT_GREY));
    //            certDescription.setText("Drop a certificate file here");
    //            break;
    //          case EXPIRED:
    //            certContentLabel.setText("Certificate expired!");
    //            certContentLabel.setTextFill(Paint.valueOf(COLOR_RED));
    //            certUsername.setText(this.authentication.getUsername());
    //            certUsername.setTextFill(Paint.valueOf(COLOR_RED));
    //            certDescription.setText("Expired " + this.authentication.getExpiration());
    //            break;
    //          case INVALID:
    //            certContentLabel.setText("Invalid certificate!");
    //            certContentLabel.setTextFill(Paint.valueOf(COLOR_RED));
    //            certUsername.setText(this.authentication.getUsername());
    //            certUsername.setTextFill(Paint.valueOf(COLOR_RED));
    //            certDescription.setText("Drop a valid certificate here");
    //          case OFFLINE:
    //            certContentLabel.setText("System is offline");
    //            certContentLabel.setTextFill(Paint.valueOf(COLOR_RED));
    //            certUsername.setText(this.authentication.getUsername());
    //            certUsername.setTextFill(Paint.valueOf(COLOR_LIGHT_GREY));
    //            certDescription.setText("Check network connection");
    //            break;
    //          case VALID:
    //            certContentLabel.setText("Certificate is valid");
    //            certContentLabel.setTextFill(Paint.valueOf("#666666"));
    //            certUsername.setText(this.authentication.getUsername());
    //            certUsername.setTextFill(Paint.valueOf(COLOR_GREEN));
    //            certDescription.setText("Expires " +
    // this.authentication.getExpiration().toString(DateTimeFormat.mediumDate()));
    //            break;
    //          default:
    //            break;
    //        }
    //
    //        int i = 0;
    //        List<Group> groups = this.authentication.getGroups();
    //        this.groupIconArea.getChildren().clear();
    //        for(Group group : groups) {
    //          if (i < 9) {
    //            int columnIndex = i % 3;
    //            int rowIndex = i / 3;
    //            Node groupIcon;
    //            Tooltip tooltip = new Tooltip();
    //            if (i == 8 && groups.size() > 9) {
    //              groupIcon = new Label("...");
    //              groupIcon.getStyleClass().add("group-icon");
    //              StringBuffer otherGroups = new StringBuffer();
    //              for(; i < groups.size(); i++) {
    //                otherGroups.append(groups.get(i).getId()).append("\n");
    //              }
    //              tooltip.setText(otherGroups.toString());
    //            } else {
    //              if (group.getIconUrl() != null && !"".equals(group.getIconUrl())) {
    //                Image groupImage = new Image(group.getIconUrl(), 24, 24, false, false);
    //                groupIcon = new ImageView(groupImage);
    //                groupIcon.setPickOnBounds(true);
    //              } else {
    //                StringBuffer lText = new
    // StringBuffer().append(String.valueOf(group.getId().charAt(0)).toUpperCase());
    //                if (group.getId().length() > 1) {
    //                  lText.append(String.valueOf(group.getId().charAt(1)).toUpperCase());
    //                }
    //                groupIcon = new Label(lText.toString());
    //                groupIcon.getStyleClass().add("group-icon");
    //                if (group.getId().equals(Authentication.DEVELOPER_GROUP)) {
    //                  groupIcon.getStyleClass().add("group-icon-developer");
    //                }
    //                // ((Label)groupIcon).setAlignment(Pos.CENTER);
    //              }
    //              tooltip = new Tooltip(group.getId());
    //            }
    //            this.groupIconArea.add(groupIcon, columnIndex, rowIndex);
    //            tooltip.setStyle("-fx-font-size: 12");
    //            Tooltip.install(groupIcon, tooltip);
    //            i++;
    //          } else {
    //            break;
    //          }
    //        }
    //        if (!authentication.getMessages().isEmpty()) {
    //          StringBuffer errors = new StringBuffer();
    //          StringBuffer warnings = new StringBuffer();
    //          StringBuffer infos = new StringBuffer();
    //          authentication.getMessages().forEach(m -> {
    //            StringBuffer buffer;
    //            if (m.getType() == HubNotificationMessage.Type.ERROR) {
    //              buffer = errors;
    //            } else if (m.getType() == HubNotificationMessage.Type.WARNING) {
    //              buffer = warnings;
    //            } else {
    //              buffer = infos;
    //            }
    //            switch(m.getMessageClass()) {
    //              case EXPIRED_GROUP:
    //              case EXPIRING_GROUP:
    //              case EXPIRING_CERTIFICATE:
    //                if (m.getInfo() != null) {
    //                  String sDate = (String) (Arrays.asList(m.getInfo()).stream()
    //                                                 .filter(c ->
    // c.getFirst().equals(HubNotificationMessage.ExtendedInfo.EXPIRATION_DATE))
    //                                                 .findFirst().get()).getSecond();
    //                  DateTime date = DateTime.parse(sDate);
    //                  if (m.getMessageClass() ==
    // HubNotificationMessage.MessageClass.EXPIRING_CERTIFICATE) {
    //                    buffer.append("Certificate ");
    //                  } else {
    //                    String group = (String) (Arrays.asList(m.getInfo()).stream()
    //                                                   .filter(c ->
    // c.getFirst().equals(HubNotificationMessage.ExtendedInfo.GROUP_NAME))
    //                                                   .findFirst().get()).getSecond();
    //                    buffer.append("Subscription to group ").append(group);
    //                  }
    //                  if
    // (m.getMessageClass().equals(HubNotificationMessage.MessageClass.EXPIRED_GROUP)) {
    //                    buffer.append(" has expired");
    //                  } else {
    //                    buffer.append(" will expire on
    // ").append(DateTimeFormat.forPattern("dd/MM/yyyy").print(date));
    //                  }
    //                } else {
    //                  buffer.append(m.getMsg());
    //                }
    //                break;
    //              default:
    //                buffer.append(m.getMsg());
    //                break;
    //            }
    //            buffer.append("\n");
    //          });
    //          if (errors.length() > 0)
    //            showExpirationAlert(Alert.AlertType.ERROR, errors.toString(), true);
    //          if (warnings.length() > 0)
    //            showExpirationAlert(Alert.AlertType.WARNING, warnings.toString(), true);
    //          if (infos.length() > 0)
    //            showExpirationAlert(Alert.AlertType.INFORMATION, infos.toString(), false);
    //        }
    //        // activate branch changer button
    //        if (this.authentication.isDeveloper()) {
    //          this.switchProjectsBranch.setVisible(true);
    //          this.switchProjectsBranch.setDisable(!this.hasRepositories());
    //          this.ccSwitchProjectsBranchTooltip.setText("Switch projects to "
    //                                                             +
    // (DEVELOP_BRANCH.equals(this.currentBranch) ? MASTER_BRANCH : DEVELOP_BRANCH) + " branch");
    //        } else {
    //          this.switchProjectsBranch.setVisible(false);
    //        }
    //
    //        this.checkForUpdates(true);
    //      }
    //
    //    }
  }

  public static class DistributionComponent extends BaseComponent {

    public DistributionComponent(Distribution distribution) {
      super(Type.Distribution, "Distribution status", true);
    }

    protected void createContent() {
      var card = new Card();
      this.getChildren().add(card);
    }
  }

  public static class Settings extends BaseComponent {

    public Settings() {
      super(Type.Settings, "Settings", true);
    }

    protected void createContent() {
      var card = new Card();

      TabPane tabPane = new TabPane();
      tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

      for (var settingPage : Setting.Page.values()) {
        Tab tab = new Tab();
        tab.setText(Utils.Strings.capitalize(settingPage.name().replace("_", " ").toLowerCase()));

        VBox content = new VBox(10);
        content.setPadding(new Insets(0));
        VBox.setVgrow(content, Priority.ALWAYS);
        content.setMinHeight(360);

        var pageSettings =
            Arrays.stream(Setting.values()).filter(s -> s.page == settingPage).toList();

        TableView<Setting> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setTableMenuButtonVisible(false);
        table
            .getStyleClass()
            .addAll(Styles.DENSE, Styles.STRIPED, Tweaks.EDGE_TO_EDGE, Tweaks.NO_HEADER);
        table.setMinHeight(360);

        TableColumn<Setting, Node> labelColumn = new TableColumn<>();
        labelColumn.setCellValueFactory(
            data -> {
              VBox labelBox = new VBox(2);
              Label nameLabel =
                  new Label(
                      Utils.Strings.capitalize(
                          data.getValue().name().replace("_", " ").toLowerCase()));
              nameLabel.setStyle("-fx-font-weight: bold");
              Label descLabel = new Label(data.getValue().description);
              descLabel.setStyle("-fx-font-size: 11px");
              descLabel.setWrapText(true);
              labelBox.getChildren().addAll(nameLabel, descLabel);
              return new SimpleObjectProperty<>(labelBox);
            });

        TableColumn<Setting, Node> inputColumn = new TableColumn<>();
        inputColumn.setCellValueFactory(
            data -> {
              Node input;
              if (data.getValue().valueClass == Boolean.class) {
                ToggleSwitch toggle = new ToggleSwitch();
                toggle.setSelected(
                    KlabIDEController.modeler()
                        .engine()
                        .getSettings()
                        .get(data.getValue(), Boolean.class));
                input = toggle;
                toggle.setOnMouseClicked(
                    e -> {
                      KlabIDEController.modeler()
                          .engine()
                          .getSettings()
                          .set(data.getValue(), toggle.isSelected());
                    });
              } else if (Integer.class.isAssignableFrom(data.getValue().valueClass)) {
                TextField field = new TextField();
                field.setText(
                    KlabIDEController.modeler()
                            .engine()
                            .getSettings()
                            .get(data.getValue(), Integer.class)
                        + "");
                field.setTextFormatter(
                    new TextFormatter<>(
                        change ->
                            change.getControlNewText().matches("-?\\d*\\.?\\d*") ? change : null));
                field.setOnAction(
                    e -> {
                      KlabIDEController.modeler()
                          .engine()
                          .getSettings()
                          .set(data.getValue(), Integer.parseInt(field.getText()));
                    });
                input = field;
              } else if (File.class.isAssignableFrom(data.getValue().valueClass)) {

                HBox fileBox = new HBox(10);
                TextField fileField = new TextField();
                fileField.setEditable(false);
                Button chooseButton = new Button("Choose...");
                fileBox.getChildren().addAll(fileField, chooseButton);
                HBox.setHgrow(fileBox, Priority.ALWAYS);
                HBox.setHgrow(fileField, Priority.ALWAYS);

                chooseButton.setOnAction(
                    e -> {
                      FileChooser fileChooser = new FileChooser();
                      fileChooser.setTitle("Select File");
                      File selectedFile = fileChooser.showOpenDialog(getScene().getWindow());
                      if (selectedFile != null) {
                        fileField.setText(selectedFile.getAbsolutePath());
                        KlabIDEController.modeler()
                            .engine()
                            .getSettings()
                            .set(data.getValue(), selectedFile);
                      }
                    });

                File currentValue =
                    KlabIDEController.modeler()
                        .engine()
                        .getSettings()
                        .get(data.getValue(), File.class);
                if (currentValue != null) {
                  fileField.setText(currentValue.getAbsolutePath());
                }

                input = fileBox;

              } else {
                // TODO
                TextField field = new TextField();
                if (data.getValue().defaultValue != null) {
                  field.setText(
                      KlabIDEController.modeler()
                          .engine()
                          .getSettings()
                          .get(data.getValue(), Object.class)
                          .toString());
                }
                field.setOnAction(
                    e -> {
                      KlabIDEController.modeler()
                          .engine()
                          .getSettings()
                          .set(
                              data.getValue(),
                              Utils.Data.parseAsType(field.getText(), data.getValue().valueClass));
                    });
                HBox.setHgrow(field, Priority.ALWAYS);
                input = field;
              }
              return new SimpleObjectProperty<>(input);
            });

        table.getColumns().addAll(labelColumn, inputColumn);
        table.getItems().addAll(pageSettings);
        table.setFixedCellSize(46);
        table
            .prefHeightProperty()
            .bind(table.fixedCellSizeProperty().multiply(Bindings.size(table.getItems())));

        ScrollPane scroll = new ScrollPane(table);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setMinHeight(360);

        content.getChildren().add(scroll);

        tab.setContent(content);
        tabPane.getTabs().add(tab);
      }

      card.setBody(tabPane);
      this.getChildren().add(card);
    }
  }

  public static class Resource extends BaseComponent {

    private final Consumer<ResourceInfo> clickHandler;
    private ResourceInfo descriptor;

    public Resource(ResourceInfo descriptor, Consumer<ResourceInfo> clickHandler) {
      super(Type.Object, descriptor.getUrn(), false);
      this.descriptor = descriptor;
      this.clickHandler = clickHandler;
      createContent();
    }

    protected void createContent() {
      var card = new Card();
      VBox body = new VBox(10);

      var icon = new FontIcon(Theme.getIcon(descriptor.getKnowledgeClass()));

      Label title = new Label(this.descriptor.getUrn());
      title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
      HBox header = new HBox(10, icon, title);
      header.setAlignment(Pos.CENTER_LEFT);

      TextArea description = new TextArea("Resource description placeholder text");
      description.setWrapText(true);
      description.setEditable(false);
      description.setPrefRowCount(3);
      body.getChildren().add(description);

      Label status = new Label("Status: Active | Last modified: 2025-05-30");
      status.setStyle("-fx-font-size: 10px;");
      HBox footer = new HBox(status);
      footer.setAlignment(Pos.CENTER_RIGHT);

      card.setOnMouseClicked(
          event -> {
            clickHandler.accept(this.descriptor);
          });

      card.setBody(body);
      card.setHeader(header);
      card.setFooter(footer);
      this.getChildren().add(card);
    }
  }

  public static class Services extends BaseComponent {

    public Services() {
      super(Type.ServiceInfo, "Services", true);
    }

    private static final String REASONER = "REASONER";
    private static final String RESOLVER = "RESOLVER";
    private static final String RESOURCES = "RESOURCES";
    private static final String RUNTIME = "RUNTIME";

    protected void createContent() {
      var card = new Card();

      Tab reasonerTab = createServiceTab("Reasoner", "REASONER", Reasoner.class);
      Tab resourcesTab = createServiceTab("Resources", "RESOURCES", ResourcesService.class);
      Tab resolverTab = createServiceTab("Resolver", "RESOLVER", Resolver.class);
      Tab runtimeTab = createServiceTab("Runtime", "RUNTIME", RuntimeService.class);

      TabPane tabs = new TabPane(reasonerTab, resourcesTab, resolverTab, runtimeTab);
      tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
      card.setBody(tabs);
      this.getChildren().add(card);
    }

    private Tab createServiceTab(
        String title,
        String serviceType,
        Class<? extends org.integratedmodelling.klab.api.services.KlabService> serviceClass) {
      Tab tab = new Tab();
      tab.setText(title);

      VBox content = new VBox(10);
      content.setPadding(new Insets(10));

      ComboBox<KlabService> serviceSelector = new ComboBox<>();
      serviceSelector.getStyleClass().add("combo-box-no-border");
      // Populate services of specified type
      var services = KlabIDEController.modeler().user().getServices(serviceClass);

      serviceSelector.getItems().addAll(services);
      serviceSelector.setMaxWidth(Double.MAX_VALUE);

      final Map<String, KlabService> serviceMap = new HashMap<>();
      services.forEach(
          service ->
              serviceMap.put(service.getServiceName() + " [" + service.getUrl() + "]", service));

      // Convert service to display string
      serviceSelector.setConverter(
          new StringConverter<org.integratedmodelling.klab.api.services.KlabService>() {
            @Override
            public String toString(KlabService service) {
              return service.getServiceName() + " [" + service.getUrl() + "]";
            }

            @Override
            public KlabService fromString(String string) {
              return serviceMap.get(string);
            }
          });

      // Create KlabService component when service selected
      serviceSelector
          .valueProperty()
          .addListener(
              (obs, oldVal, newVal) -> {
                content.getChildren().clear();
                content.getChildren().add(serviceSelector);

                if (newVal != null) {
                  KlabServicePanel serviceComponent = new KlabServicePanel(newVal);
                  content.getChildren().add(serviceComponent);
                }
              });

      content.getChildren().add(serviceSelector);

      if (!services.isEmpty()) {
        serviceSelector.getSelectionModel().selectFirst();
      } else {
        serviceSelector.setPlaceholder(
            new Label("No " + title.toLowerCase() + " services available"));
      }

      tab.setContent(content);
      return tab;
    }
  }

  public static class DigitalTwin extends BaseComponent {

    private final Consumer<ContextScope> deleteAction;
    ContextInfo digitalTwin;
    Consumer<ContextScope> selectAction;

    public DigitalTwin(
        ContextInfo digitalTwin,
        Consumer<ContextScope> selectAction,
        Consumer<ContextScope> deleteAction) {
      super(Type.Object, digitalTwin.getName(), false);
      this.digitalTwin = digitalTwin;
      this.selectAction = selectAction;
      this.deleteAction = deleteAction;
    }

    @Override
    protected void createContent() {
      var card = new Card();
      VBox content = new VBox(10);
      content.setPadding(new Insets(10));
      VBox.setVgrow(content, Priority.ALWAYS);
      content.setPrefWidth(280);

      Label title = new Label(digitalTwin.getName());
      title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
      title.setMaxWidth(Double.MAX_VALUE);

      Button openButton = new Button();
      openButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
      openButton.setGraphic(new FontIcon(Material2MZ.OPEN_IN_NEW));
      openButton.setOnAction(
          e -> {
            if (selectAction != null) {
              selectAction.accept(
                  KlabIDEController.modeler().user().connect(digitalTwin.getConfiguration()));
            }
          });

      Button linkButton = new Button();
      linkButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
      linkButton.setGraphic(new FontIcon(Material2AL.CONTENT_COPY));
      linkButton.setOnAction(
          e -> {
            if (selectAction != null) {
              final var clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
              final var ct = new javafx.scene.input.ClipboardContent();
              ct.putString(digitalTwin.getConfiguration().getUrl().toString());
              clipboard.setContent(ct);
            }
          });

      Button deleteButton = new Button();
      deleteButton.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
      deleteButton.setGraphic(new FontIcon(Material2AL.DELETE_FOREVER));
      deleteButton.setOnAction(
          e -> {
            var peer = KlabIDEController.instance().getDigitalTwinPeer(digitalTwin.getId());
            if (peer != null) {
              peer.closeScope();
              deleteAction.accept(peer.scope());
            } else {
              var scope =
                  KlabIDEController.modeler().user().connect(digitalTwin.getConfiguration());
              if (scope != null) {
                scope.close();
              }
              if (deleteAction != null) {
                deleteAction.accept(scope);
              }
            }
          });

      HBox buttonContainer = new HBox();
      buttonContainer.setSpacing(0);
      buttonContainer.setAlignment(Pos.CENTER_LEFT);
      buttonContainer.getChildren().addAll(openButton, linkButton, deleteButton);

      HBox titleBox = new HBox(5);
      titleBox.setAlignment(Pos.CENTER_LEFT);
      HBox.setHgrow(title, Priority.ALWAYS);
      titleBox.getChildren().addAll(title, buttonContainer);

      HBox.setHgrow(titleBox, Priority.ALWAYS);

      if (digitalTwin.getUser() != null && digitalTwin.getUser().contains("@")) {
        FontIcon federatedIcon = new FontIcon(Material2AL.CLOUD);
        federatedIcon.setStyle("-fx-font-size: 14px;");
        Tooltip.install(federatedIcon, new Tooltip("Federated user: " + digitalTwin.getUser()));
        titleBox.getChildren().add(federatedIcon);
      }

      Hyperlink url = new Hyperlink(digitalTwin.getConfiguration().getUrl().toString());
      url.setStyle("-fx-font-size: 10px;");
      url.setOnAction(
          e ->
              KlabIDEApplication.instance()
                  .getHostServices()
                  .showDocument(digitalTwin.getConfiguration().getUrl().toString()));

      Label size =
          new Label(String.format("Size: %d observations", digitalTwin.getObservationCount()));
      size.setStyle("-fx-font-size: 12px;");

      TextArea description = new TextArea(digitalTwin.getDescription());
      description.setWrapText(true);
      description.setEditable(false);
      description.setPrefRowCount(3);

      Label persistence =
          new Label("Persistence: " + digitalTwin.getConfiguration().getPersistence());
      persistence.setStyle("-fx-font-size: 12px;");

      content.getChildren().addAll(titleBox, url, size, description, persistence);

      card.setBody(content);
      VBox.setVgrow(card, Priority.ALWAYS);
      this.getChildren().add(card);
    }
  }

  /** A component that demonstrates the AutoScrollPane with a list of sample components. */
  public static class AutoScrollDemo extends BaseComponent {

    public AutoScrollDemo() {
      super(Type.Object, "Auto Scroll Demo", true);
    }

    @Override
    protected void createContent() {
      var card = new Card();
      VBox content = new VBox(20);
      content.setPadding(new Insets(20));

      // Create some sample components to scroll
      List<Node> horizontalComponents = new ArrayList<>();
      for (int i = 1; i <= 5; i++) {
        Label label = new Label("Horizontal Component " + i);
        label.setPrefWidth(200);
        label.setPrefHeight(100);
        label.setAlignment(Pos.CENTER);
        label.setStyle(
            "-fx-background-color: "
                + getRandomColor()
                + "; -fx-text-fill: white; -fx-font-weight: bold;");
        horizontalComponents.add(label);
      }

      // Create a horizontal auto-scroll pane
      AutoScrollPane horizontalScroller = new AutoScrollPane(Orientation.HORIZONTAL, 50);
      horizontalScroller.setPrefHeight(120);
      horizontalScroller.setPrefWidth(400);
      horizontalScroller.setComponents(horizontalComponents);

      // Create some sample components to scroll vertically
      List<Node> verticalComponents = new ArrayList<>();
      for (int i = 1; i <= 5; i++) {
        Label label = new Label("Vertical Component " + i);
        label.setPrefWidth(200);
        label.setPrefHeight(100);
        label.setAlignment(Pos.CENTER);
        label.setStyle(
            "-fx-background-color: "
                + getRandomColor()
                + "; -fx-text-fill: white; -fx-font-weight: bold;");
        verticalComponents.add(label);
      }
      // Create a vertical auto-scroll pane
      AutoScrollPane verticalScroller = new AutoScrollPane(Orientation.VERTICAL, 50);
      verticalScroller.setPrefHeight(300);
      verticalScroller.setPrefWidth(220);
      verticalScroller.setComponents(verticalComponents);

      // Create controls for the horizontal scroller
      Label horizontalLabel = new Label("Horizontal Scroller");
      horizontalLabel.setStyle("-fx-font-weight: bold;");
      Slider horizontalSpeedSlider = new Slider(10, 200, 50);
      horizontalSpeedSlider.setShowTickLabels(true);
      horizontalSpeedSlider.setShowTickMarks(true);
      horizontalSpeedSlider
          .valueProperty()
          .addListener(
              (obs, oldVal, newVal) -> {
                horizontalScroller.setScrollSpeed(newVal.doubleValue());
              });
      Button horizontalToggleButton = new Button("Pause");
      horizontalToggleButton.setOnAction(
          e -> {
            if (horizontalToggleButton.getText().equals("Pause")) {
              horizontalScroller.stopScrolling();
              horizontalToggleButton.setText("Resume");
            } else {
              horizontalScroller.startScrolling();
              horizontalToggleButton.setText("Pause");
            }
          });
      HBox horizontalControls =
          new HBox(10, new Label("Speed:"), horizontalSpeedSlider, horizontalToggleButton);
      horizontalControls.setAlignment(Pos.CENTER_LEFT);

      // Create controls for the vertical scroller
      Label verticalLabel = new Label("Vertical Scroller");
      verticalLabel.setStyle("-fx-font-weight: bold;");
      Slider verticalSpeedSlider = new Slider(10, 200, 50);
      verticalSpeedSlider.setShowTickLabels(true);
      verticalSpeedSlider.setShowTickMarks(true);
      verticalSpeedSlider
          .valueProperty()
          .addListener(
              (obs, oldVal, newVal) -> {
                verticalScroller.setScrollSpeed(newVal.doubleValue());
              });
      Button verticalToggleButton = new Button("Pause");
      verticalToggleButton.setOnAction(
          e -> {
            if (verticalToggleButton.getText().equals("Pause")) {
              verticalScroller.stopScrolling();
              verticalToggleButton.setText("Resume");
            } else {
              verticalScroller.startScrolling();
              verticalToggleButton.setText("Pause");
            }
          });
      HBox verticalControls =
          new HBox(10, new Label("Speed:"), verticalSpeedSlider, verticalToggleButton);
      verticalControls.setAlignment(Pos.CENTER_LEFT);

      // Add everything to the content
      VBox horizontalSection =
          new VBox(10, horizontalLabel, horizontalScroller, horizontalControls);
      VBox verticalSection = new VBox(10, verticalLabel, verticalScroller, verticalControls);
      HBox scrollers = new HBox(20, horizontalSection, verticalSection);
      content.getChildren().add(scrollers);

      card.setBody(content);
      this.getChildren().add(card);
    }

    private String getRandomColor() {
      String[] colors = {
        "#3498db", // Blue
        "#e74c3c", // Red
        "#2ecc71", // Green
        "#f39c12", // Orange
        "#9b59b6" // Purple
      };
      return colors[(int) (Math.random() * colors.length)];
    }
  }

  /** A component that demonstrates the Timeline with configurable time intervals. */
  public static class TimelineComponent extends BaseComponent {

    public TimelineComponent() {
      super(Type.Object, "Timeline Demo", true);
    }

    @Override
    protected void createContent() {
      this.getChildren().add(TimelineDemo.createDemo());
    }
  }

  public static class KlabServicePanel extends VBox {
    private final KlabService service;
    private TabPane tabPane;
    //    private VBox exportPane;
    private ComboBox<String> schemaSelector;
    private VBox parameterForm;
    private VBox dropTarget;

    public KlabServicePanel(KlabService service) {
      this.service = service;
      createContent();
    }

    protected void createContent() {
      var card = new Card();
      VBox content = new VBox(10);
      content.setPadding(new Insets(10));

      Label nameLabel =
          new Label(
              "Service: "
                  + service.capabilities(KlabIDEController.modeler().user()).getServiceName());
      nameLabel.setStyle("-fx-font-weight: bold");

      Hyperlink hostLink = new Hyperlink(service.getUrl().toString());
      hostLink.setOnAction(
          e ->
              KlabIDEApplication.instance()
                  .getHostServices()
                  .showDocument(service.getUrl() + "/public/capabilities"));

      Hyperlink apiLink = new Hyperlink("API Documentation");
      apiLink.setOnAction(
          e ->
              KlabIDEApplication.instance()
                  .getHostServices()
                  .showDocument(service.getUrl() + "/api.html"));

      tabPane = new TabPane();
      tabPane.setBorder(Border.EMPTY);
      Tab infoTab = new Tab("Info");
      infoTab.setClosable(false);
      VBox infoContent = new VBox(10, nameLabel, hostLink, apiLink);
      //      infoContent.setPadding(new Insets(10));
      infoTab.setContent(infoContent);

      //      Tab exportTab = new Tab("Export");
      //      exportTab.setClosable(false);
      //      exportPane = new VBox(10);
      //      exportPane.setPadding(new Insets(10));

      //      schemaSelector = new ComboBox<>();
      //      schemaSelector.setPromptText("Select Export Schema");
      //      schemaSelector
      //          .getItems()
      //          .addAll(
      //              service
      //                  .capabilities(KlabIDEController.modeler().user())
      //                  .getExportSchemata()
      //                  .keySet());
      //      schemaSelector.setOnAction(e -> updateExportForm());

      parameterForm = new VBox(2);
      //      parameterForm.setSpacing(10);

      ScrollPane scrollPane = new ScrollPane();
      scrollPane.setContent(parameterForm);
      scrollPane.setFitToWidth(true);
      scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
      scrollPane.setMinHeight(360);
      VBox.setVgrow(scrollPane, Priority.ALWAYS);

      //      dropTarget = new VBox(10);
      //      dropTarget.setAlignment(Pos.CENTER);
      //      dropTarget.setPrefHeight(200);
      //      dropTarget.setStyle(
      //          "-fx-border-color: #cccccc; -fx-border-style: dashed; -fx-border-radius: 5;");

      //      Label dropLabel = new Label("Drop files here");
      //      dropTarget.getChildren().add(dropLabel);
      //
      //      dropTarget.setOnDragOver(
      //          event -> {
      //            event.acceptTransferModes(TransferMode.COPY);
      //            event.consume();
      //          });
      //
      //      dropTarget.setOnDragDropped(
      //          event -> {
      //            // Handle file drop
      //            event.setDropCompleted(true);
      //            event.consume();
      //          });

      //      exportPane.getChildren().addAll(schemaSelector, parameterForm /*, dropTarget*/);
      //      exportTab.setContent(exportPane);

      Tab importTab = new Tab("Import");
      importTab.setClosable(false);

      VBox importPane = new VBox(10);
      importPane.setPadding(new Insets(10));
      importPane.setMinHeight(360);

      ComboBox<String> importSchemaSelector = new ComboBox<>();
      importSchemaSelector.setPromptText("Select Import Schema");
      var importSchemata =
          service.capabilities(KlabIDEController.modeler().user()).getImportSchemata();
      final var schemaKey = new HashMap<String, ResourceTransport.Schema>();
      for (var schemaName : importSchemata.keySet()) {
        for (var schema : importSchemata.get(schemaName)) {
          var description =
              schema.getProperties().isEmpty()
                  ? " (" + Utils.Strings.join(schema.getMediaTypes(), ", ") + ")"
                  : " (parameters)";
          var name = schemaName + description;
          importSchemaSelector.getItems().add(name);
          schemaKey.put(name, schema);
        }
      }
      importSchemaSelector.setOnAction(
          e -> updateImportForm(schemaKey.get(importSchemaSelector.getValue())));

      importPane.getChildren().addAll(importSchemaSelector, scrollPane);
      importTab.setContent(importPane);

      tabPane.getTabs().addAll(infoTab, /*exportTab, */ importTab);
      content.getChildren().addAll(tabPane);
      VBox.setVgrow(tabPane, Priority.ALWAYS);

      card.setBody(content);
      card.setMinHeight(440);
      this.getChildren().add(card);
    }

    private void updateImportForm(ResourceTransport.Schema schema) {

      parameterForm.getChildren().clear();

      Map<String, Object> userInput = new HashMap<>();
      AtomicReference<File> file = new AtomicReference<>();

      if (schema.getType() == ResourceTransport.Schema.Type.PROPERTIES) {
        var parameters = schema.getProperties();
        for (var parameter : parameters.entrySet()) {
          Label label = new Label(parameter.getKey());
          if (parameter.getValue().optional()) {
            label.setStyle("-fx-font-weight: bold;");
          } else {
            label.setStyle("-fx-text-fill: #dd0000; -fx-font-weight: bold;");
          }
          TextField input = new TextField();
          input.setPromptText(parameter.getValue().defaultValue());
          input
              .textProperty()
              .addListener(
                  (observable, oldValue, newValue) -> {
                    userInput.put(parameter.getKey(), newValue);
                  });
          parameterForm.getChildren().addAll(label, input);
        }
      } else {

        String targetDir = System.getProperty("user.home"); // TODO preset, recover extension
        String promptText = "Drop file or URL to upload";

        // Create callback for successful uploads
        Consumer<File> onSuccess =
            (uploadedFile) -> {
              file.set(uploadedFile);
              KlabIDEApplication.instance()
                  .handleNotifications(List.of(Notification.info("File upload successful")));
            };

        // Create error handler
        BiConsumer<String, Throwable> onError =
            (message, throwable) -> {
              KlabIDEApplication.instance()
                  .handleNotifications(List.of(Notification.error("Upload error: " + message)));
            };

        // Create the upload box
        UploadBox uploadBox = new UploadBox(targetDir, promptText, onSuccess, onError);

        parameterForm.getChildren().add(uploadBox);
      }

      Button submitButton = new Button("Submit");
      submitButton.setOnAction(
          event -> {
            Thread.ofVirtual()
                .start(
                    () -> {
                      var asset =
                          file.get() == null ? schema.asset(userInput) : schema.asset(file.get());
                      if (asset.isEmpty()) {
                        KlabIDEApplication.instance()
                            .handleNotifications(
                                List.of(
                                    Notification.error(
                                        "Import failed: specifications are incomplete")));
                        return;
                      }
                      var urn =
                          service.importAsset(
                              schema, asset, null, KlabIDEController.modeler().user());
                      var notification =
                          (urn == null || urn.isEmpty())
                              ? Notification.error("Import failed")
                              : Notification.info("Import successful: URN is " + urn);
                      KlabIDEApplication.instance().handleNotifications(List.of(notification));
                    });
          });
      HBox buttonBox = new HBox(10, submitButton);
      buttonBox.setAlignment(Pos.CENTER_RIGHT);
      buttonBox.setPadding(new Insets(10, 0, 0, 0));

      parameterForm.getChildren().add(buttonBox);
    }
  }
}

package org.integratedmodelling.klab.ide.components.cards;

import java.util.ArrayList;
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.carbonicons.CarbonIcons;
import org.kordamp.ikonli.material2.Material2MZ;

public class UserViewComponent extends BaseAssetViewComponent {

  private UserScope user;
  private Label usernameLabel;
  private Label emailLabel;
  private Label licenseLabel;
  private Label statusLabel;
  private GridPane groupIcons;
  private VBox groupArea;
  private Label federationLabel;
  private final Supplier<String> installationRejection;
  private final Function<File, CompletableFuture<UserScope>> certificateInstaller;
  private boolean installing;
  private Label dropLabel;
  private VBox dropZone;
  private static final PseudoClass DRAG_OVER = PseudoClass.getPseudoClass("drag-over");
  private static final PseudoClass BUSY = PseudoClass.getPseudoClass("busy");
  private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

  public UserViewComponent(UserScope userScope) {
    this(userScope, () -> KlabIDEController.instance().certificateInstallationRejection(),
        file -> KlabIDEController.instance().installCertificateAsync(file));
  }

  UserViewComponent(UserScope userScope, Supplier<String> rejection,
      Function<File, CompletableFuture<UserScope>> installer) {
    super(AssetViewComponent.Type.UserInfo, "User information", false);
    this.user = userScope;
    this.installationRejection = rejection;
    this.certificateInstaller = installer;
    createContent();
  }

  @Override
  public String getDescription() {
    return "Manage your user information and groups.";
  }

  @Override
  public Ikon getIcon() {
    return CarbonIcons.USER_AVATAR_FILLED_ALT;
  }

  protected Node createContent() {

    var icon = new IconLabel(Material2MZ.PERSON, 32, "-color-fg-default");

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

    var federation = Klab.INSTANCE.getFederationData(user.getUser());
    federationLabel =
        new Label(federation == null ? "Not federated" : ("Federated in " + federation.getId()));

    VBox userInfoArea = new VBox(5);
    userInfoArea.setAlignment(Pos.TOP_LEFT);
    userInfoArea
        .getChildren()
        .addAll(
            new HBox(10, icon, usernameLabel),
            emailLabel,
            licenseLabel,
            statusLabel,
            federationLabel);
    HBox.setHgrow(userInfoArea, Priority.ALWAYS);

    groupIcons = new GridPane();
    groupIcons.setHgap(5);
    groupIcons.setVgap(5);

    var groups = new ArrayList<>(user.getUser().getGroups());

    Label groupsLabel = new Label("Groups (" + groups.size() + ")");
    groupsLabel.setStyle("-fx-font-weight: bold;");

    groupArea = new VBox(5);
    groupArea.getChildren().addAll(groupsLabel, groupIcons);

    int i = 0;
    int row = 0, col = 0;

    for (var group : groups) {
      if (i < 16) {
        int columnIndex = i % 4;
        int rowIndex = i / 4;
        Node groupIcon;
        Tooltip tooltip = new Tooltip();
        if (i == 15 && groups.size() > 16) {
          groupIcon = new Label("...");
          groupIcon.getStyleClass().add("group-icon");
          StringBuffer otherGroups = new StringBuffer();
          for (; i < groups.size(); i++) {
            otherGroups.append(groups.get(i).getId()).append("\n");
          }
          tooltip.setText(otherGroups.toString());
        } else {
          if (group.getIconUrl() != null && !"".equals(group.getIconUrl())) {
            Image groupImage = new Image(group.getIconUrl(), 29, 29, false, false);
            groupIcon = new ImageView(groupImage);
            groupIcon.setPickOnBounds(true);
          } else {
            StringBuffer lText =
                new StringBuffer().append(String.valueOf(group.getId().charAt(0)).toUpperCase());
            if (group.getId().length() > 1) {
              lText.append(String.valueOf(group.getId().charAt(1)).toUpperCase());
            }
            groupIcon = new Label(lText.toString());
            groupIcon.getStyleClass().add("group-icon");
            if (group.getId().equals(Authentication.DEVELOPERS_GROUP)) {
              groupIcon.getStyleClass().add("group-icon-developer");
            }
            // ((Label)groupIcon).setAlignment(Pos.CENTER);
          }
          tooltip = new Tooltip(group.getId());
        }
        this.groupIcons.add(groupIcon, columnIndex, rowIndex);
        tooltip.setStyle("-fx-font-size: 12");
        Tooltip.install(groupIcon, tooltip);
        col++;
        if (col >= 4) {
          col = 0;
          row++;
        }
        i++;
      } else {
        break;
      }
    }

    //      int row = 0, col = 0;
    //      for (var group : user.getUser().getGroups()) {
    //        Label groupIcon =
    //            new Label(
    //                group.getName().substring(0, Math.min(2,
    // group.getName().length())).toUpperCase());
    //        groupIcon.setStyle(
    //            "-fx-background-color: #e0e0e0; -fx-padding: 5 10; -fx-background-radius: 3;");
    //        Tooltip.install(groupIcon, new Tooltip(group.getName()));
    //        groupIcons.add(groupIcon, col, row);
    //        col++;
    //        if (col > 2) {
    //          col = 0;
    //          row++;
    //        }
    //      }

    dropZone = new VBox(10);
    dropZone.setAlignment(Pos.CENTER);
    dropZone.setPrefWidth(200);
    dropZone.setPrefHeight(180);
    dropZone.getStyleClass().add("user-certificate-drop-zone");

    dropZone.setPadding(new Insets(12));
    dropLabel = new Label("Drop a new klab.cert certificate");
    dropLabel.setWrapText(true);
    dropLabel.getStyleClass().add("user-certificate-drop-zone-label");
    dropZone.getChildren().add(dropLabel);

    dropZone.setOnDragOver(
        event -> {
          if (!installing) {
            var rejection = installationRejection.get();
            boolean acceptable = acceptsCertificate(event.getDragboard().getFiles()) && rejection == null;
            dropZone.pseudoClassStateChanged(DRAG_OVER, acceptable);
            dropZone.pseudoClassStateChanged(ERROR, !acceptable);
            dropLabel.setText(rejection != null ? rejection : acceptable
                ? "Release to install and authenticate"
                : "Drop one file named klab.cert or starting with klab.cert");
            if (acceptable) event.acceptTransferModes(TransferMode.COPY);
          }
          event.consume();
        });

    dropZone.setOnDragExited(event -> {
      dropZone.pseudoClassStateChanged(DRAG_OVER, false);
      if (!installing && !dropZone.getPseudoClassStates().contains(ERROR)) {
        dropLabel.setText("Drop a new klab.cert certificate");
      }
      event.consume();
    });

    dropZone.setOnDragDropped(
        event -> {
          dropZone.pseudoClassStateChanged(DRAG_OVER, false);
          var files = event.getDragboard().getFiles();
          var rejection = installationRejection.get();
          boolean accepted = !installing && acceptsCertificate(files) && rejection == null;
          if (accepted) installCertificate(files.getFirst());
          else if (!installing) {
            dropZone.pseudoClassStateChanged(ERROR, true);
            dropLabel.setText(rejection != null ? rejection : "Drop one readable klab.cert file");
          }
          event.setDropCompleted(accepted);
          event.consume();
        });

    HBox main = new HBox(20, userInfoArea, groupArea, dropZone);
    main.setPadding(new Insets(10));
    //      var card = new Card();
    //      card.setBody(main);

    this.getChildren().add(main);

    return main;
  }

  static boolean acceptsCertificate(List<File> files) {
    return files != null && files.size() == 1 && files.getFirst().isFile()
        && files.getFirst().canRead() && files.getFirst().getName().startsWith("klab.cert");
  }

  void installCertificate(File file) {
    installing = true;
    dropZone.pseudoClassStateChanged(ERROR, false);
    dropZone.pseudoClassStateChanged(BUSY, true);
    dropLabel.setText("Installing certificate and authenticating…");
    var progress = new ProgressIndicator();
    progress.setPrefSize(28, 28);
    dropZone.getChildren().addFirst(progress);
    CompletableFuture<UserScope> installation;
    try {
      installation = certificateInstaller.apply(file);
    } catch (RuntimeException e) {
      installation = CompletableFuture.failedFuture(e);
    }
    installation.whenComplete((authenticated, failure) -> Platform.runLater(() -> {
      installing = false;
      if (authenticated != null) {
        user = authenticated;
        getChildren().clear();
        createContent();
      } else {
        dropZone.getChildren().remove(progress);
      }
      dropZone.pseudoClassStateChanged(BUSY, false);
      boolean success = failure == null && authenticated != null
          && authenticated.getUser().isAuthenticated() && !authenticated.getUser().isAnonymous();
      dropZone.pseudoClassStateChanged(ERROR, !success);
      if (failure != null) {
        var cause = failure;
        while ((cause instanceof java.util.concurrent.CompletionException
            || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
          cause = cause.getCause();
        }
        dropLabel.setText("Certificate installation failed: " + cause.getMessage());
      } else {
        dropLabel.setText(success ? "Certificate installed. Authenticated as " + user.getUser().getUsername()
            : "Certificate installed, but authentication did not succeed. Check the Hub connection and certificate.");
      }
    }));
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

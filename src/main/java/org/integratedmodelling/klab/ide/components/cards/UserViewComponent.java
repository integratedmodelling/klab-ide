package org.integratedmodelling.klab.ide.components.cards;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.integratedmodelling.common.authentication.Authentication;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.ide.components.generic.IconLabel;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.carbonicons.CarbonIcons;
import org.kordamp.ikonli.material2.Material2MZ;

import java.util.ArrayList;

public class UserViewComponent extends BaseAssetViewComponent {

  private UserScope user;
  private Label usernameLabel;
  private Label emailLabel;
  private Label licenseLabel;
  private Label statusLabel;
  private GridPane groupIcons;
  private VBox groupArea;
  private Label federationLabel;

  public UserViewComponent(UserScope userScope) {
    super(AssetViewComponent.Type.UserInfo, "User information", false);
    this.user = userScope;
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
            Image groupImage = new Image(group.getIconUrl(), 32, 32, false, false);
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

    VBox dropZone = new VBox();
    dropZone.setAlignment(Pos.CENTER);
    dropZone.setPrefWidth(200);
    dropZone.setPrefHeight(180);
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
    //      var card = new Card();
    //      card.setBody(main);

    this.getChildren().add(main);

    return main;
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

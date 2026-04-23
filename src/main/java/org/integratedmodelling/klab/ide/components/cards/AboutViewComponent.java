package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.ide.KlabIDEApplication;
import org.integratedmodelling.klab.ide.components.generic.AutoScrollPane;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AboutViewComponent extends BaseAssetViewComponent {

  public AboutViewComponent() {
    super(AssetViewComponent.Type.About, "About k.LAB", true);
  }

  // TODO integrate these in a collapsable "Credits" box.
  public static String[] credits = {
    "Sentinel-2 cloudless - https://s2maps.eu by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2024)"
    // TODO add whatever
  };

  @Override
  public String getDescription() {
    return "General information and links to k.LAB documentation";
  }

  @Override
  public Ikon getIcon() {
    return Material2AL.INFO;
  }

  protected Node createContent() {

    //      var card = new Pane();
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
        List.of("Ferdinando Villa", "Enrico Girotto", "Andrea Antonello", "Arnab Moitra")) {
      Label chip = new Label(developer);
      chip.getStyleClass().addAll(Styles.BG_NEUTRAL_SUBTLE, Styles.ROUNDED);
      chip.setPadding(new Insets(3, 10, 3, 10));
      chip.setStyle("-fx-font-size: 10px;");
      developers.add(chip);
      //
      //        Label label = new Label(developer);
      //        label.setAlignment(Pos.CENTER);
      //        label.setPadding(new Insets(10));
      //        label.setStyle("-fx-font-size: 10px;");
      //        developers.add(label);
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

    //      card.setBody(content);
    this.getChildren().add(content);

    return content;
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
    label.setStyle("-fx-padding: 5 10; -fx-background-color: #f0f0f0; -fx-background-radius: 15;");
    return label;
  }
}

package org.integratedmodelling.klab.ide.components.cards;

import devtoolsfx.gui.GUI;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.ide.KlabIDEApplication;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.integratedmodelling.klab.ide.utils.AppContext;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.evaicons.Evaicons;

import java.util.List;

public class SettingsViewComponent extends BaseAssetViewComponent {

  public SettingsViewComponent() {
    super(AssetViewComponent.Type.Settings, "Settings", true);
  }

  @Override
  public Ikon getIcon() {
    return Evaicons.OPTIONS_OUTLINE;
  }

  protected Node createContent() {
    //      var card = new Card();

    TabPane tabPane = new TabPane();
    tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

    // service settings are handled in the Services tab
    for (var settingPage :
        List.of(
            Setting.Page.GENERAL,
            Setting.Page.APPEARANCE,
            Setting.Page.EDITOR,
            Setting.Page.SERVICES,
            Setting.Page.MESSAGING,
            Setting.Page.DEBUGGING)) {

      Tab tab = new Tab();
      tab.setText(Utils.Strings.capitalize(settingPage.name().replace("_", " ").toLowerCase()));
      tab.setContent(
          new SettingsPageViewComponent(settingPage) {
            @Override
            protected void onChangedSetting(Setting setting, Object newValue) {
              if (setting == Setting.LAUNCH_DEBUG_GUI) {
                Platform.runLater(
                    () -> {
                      GUI.openToolStage(
                          KlabIDEApplication.primaryStage(),
                          KlabIDEApplication.instance().getHostServices(),
                          "DevToolsFX");
                    });
              } else if (setting == Setting.LAUNCH_DATABASE_INSPECTOR) {
                HostServices hs = AppContext.getHostServices();
                if (hs != null) {
                  hs.showDocument("https://browser.neo4j.io/");
                }
              } else {
                KlabIDEController.instance()
                    .engine()
                    .getSettings()
                    .set(setting, Utils.Data.parseAsType(newValue.toString(), setting.valueClass));
              }
            }
          });
      tabPane.getTabs().add(tab);
    }

    //      card.setBody(tabPane);
    this.getChildren().add(tabPane);
    return tabPane;
  }
}

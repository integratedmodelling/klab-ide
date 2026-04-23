package org.integratedmodelling.klab.ide.components.cards;

import atlantafx.base.controls.Card;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign.MaterialDesign;

import java.util.HashMap;
import java.util.Map;

public class ServicesViewComponent extends BaseAssetViewComponent {

  public ServicesViewComponent() {
    super(AssetViewComponent.Type.ServiceInfo, "Services", true);
  }

  @Override
  public String getDescription() {
    return "Service information, settings and management dashboard";
  }

  @Override
  public Ikon getIcon() {
    return MaterialDesign.MDI_SERVER_NETWORK;
  }

  protected Node createContent() {
    var card = new Card();

    Tab reasonerTab = createServiceTab("Reasoner", "REASONER", Reasoner.class);
    Tab resourcesTab = createServiceTab("Resources", "RESOURCES", ResourcesService.class);
    Tab resolverTab = createServiceTab("Resolver", "RESOLVER", Resolver.class);
    Tab runtimeTab = createServiceTab("Runtime", "RUNTIME", RuntimeService.class);

    TabPane tabs = new TabPane(reasonerTab, resourcesTab, resolverTab, runtimeTab);
    tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    card.setBody(tabs);
    this.getChildren().add(card);
    return card;
  }

  private Tab createServiceTab(
      String title, String serviceType, Class<? extends KlabService> serviceClass) {
    Tab tab = new Tab();
    tab.setText(title);

    VBox content = new VBox(10);
    content.setPadding(new Insets(10));

    ComboBox<KlabService> serviceSelector = new ComboBox<>();
    serviceSelector.getStyleClass().add("combo-box-no-border");
    // Populate services of specified type
    var services = KlabIDEController.instance().user().getServices(serviceClass);

    serviceSelector.getItems().addAll(services);
    serviceSelector.setMaxWidth(Double.MAX_VALUE);

    final Map<String, KlabService> serviceMap = new HashMap<>();
    services.forEach(
        service -> {
          if (service != null) {
            serviceMap.put(service.serviceName() + " [" + service.getUrl() + "]", service);
          }
        });

    // Convert service to display string
    serviceSelector.setConverter(
        new StringConverter<KlabService>() {
          @Override
          public String toString(KlabService service) {
            return service != null
                ? service.serviceName() + " [" + service.getUrl() + "]"
                : "[no service available]";
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
                ServicePageViewComponent serviceComponent =
                    new ServicePageViewComponent(newVal);
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

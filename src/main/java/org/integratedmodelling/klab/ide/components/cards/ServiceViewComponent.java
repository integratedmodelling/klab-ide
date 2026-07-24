package org.integratedmodelling.klab.ide.components.cards;

import java.util.HashMap;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.ide.KlabIDEController;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign.MaterialDesign;

/** Displays the available instances and dashboard for one service category. */
public class ServiceViewComponent extends BaseAssetViewComponent {

  private final KlabService.Type serviceType;

  public ServiceViewComponent(KlabService.Type serviceType) {
    super(componentType(serviceType), title(serviceType), false);
    this.serviceType = serviceType;
    createContent();
  }

  @Override
  public String getDescription() {
    return title(serviceType) + " service information, settings and management";
  }

  @Override
  public Ikon getIcon() {
    return MaterialDesign.MDI_SERVER_NETWORK;
  }

  @Override
  protected Node createContent() {
    VBox content = new VBox(10);
    content.setPadding(new Insets(10));

    ComboBox<KlabService> serviceSelector = new ComboBox<>();
    serviceSelector.getStyleClass().add("combo-box-no-border");
    var services =
        KlabIDEController.instance().user().getServices(serviceClass(serviceType));

    serviceSelector.getItems().addAll(services);
    serviceSelector.setMaxWidth(Double.MAX_VALUE);

    final Map<String, KlabService> serviceMap = new HashMap<>();
    services.forEach(
        service -> {
          if (service != null) {
            serviceMap.put(service.serviceName() + " [" + service.getUrl() + "]", service);
          }
        });

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
                VBox.setVgrow(serviceComponent, Priority.ALWAYS);
              }
            });

    content.getChildren().add(serviceSelector);

    if (!services.isEmpty()) {
      serviceSelector.getSelectionModel().selectFirst();
    } else {
      serviceSelector.setPlaceholder(
          new Label("No " + title(serviceType).toLowerCase() + " services available"));
    }

    getChildren().add(content);
    return content;
  }

  private static AssetViewComponent.Type componentType(KlabService.Type type) {
    return switch (type) {
      case REASONER -> AssetViewComponent.Type.ReasonerService;
      case RESOURCES -> AssetViewComponent.Type.ResourcesService;
      case RESOLVER -> AssetViewComponent.Type.ResolverService;
      case RUNTIME -> AssetViewComponent.Type.RuntimeService;
      default -> throw new IllegalArgumentException("Unsupported service type: " + type);
    };
  }

  private static String title(KlabService.Type type) {
    return switch (type) {
      case REASONER -> "Reasoner";
      case RESOURCES -> "Resources";
      case RESOLVER -> "Resolver";
      case RUNTIME -> "Runtime";
      default -> throw new IllegalArgumentException("Unsupported service type: " + type);
    };
  }

  private static Class<? extends KlabService> serviceClass(KlabService.Type type) {
    return switch (type) {
      case REASONER -> Reasoner.class;
      case RESOURCES -> ResourcesService.class;
      case RESOLVER -> Resolver.class;
      case RUNTIME -> RuntimeService.class;
      default -> throw new IllegalArgumentException("Unsupported service type: " + type);
    };
  }
}

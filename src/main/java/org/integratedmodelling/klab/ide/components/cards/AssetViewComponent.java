package org.integratedmodelling.klab.ide.components.cards;

import org.kordamp.ikonli.Ikon;

public interface AssetViewComponent {

  Type getType();

  String getTitle();

  String getDescription();

  Ikon getIcon();

  enum Type {
    Distribution,
    Message,
    UserInfo,
    ReasonerService,
    ResourcesService,
    ResolverService,
    RuntimeService,
    Help,
    About,
    Settings,
    //    AutoScroll, // Auto-scrolling component
    Object // these are not indexed and may be used outside the notebook
  }
}

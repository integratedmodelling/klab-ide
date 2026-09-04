package org.integratedmodelling.klab.ide.components;

import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableDocument;
import org.integratedmodelling.klab.ide.Theme;
import org.integratedmodelling.klab.ide.components.generic.IconButton;
import org.integratedmodelling.klab.modeler.model.NavigableKlabDocument;
import org.integratedmodelling.klabeditor.MonacoEditorView;
import org.kordamp.ikonli.bootstrapicons.BootstrapIcons;
import org.kordamp.ikonli.codicons.Codicons;
import org.kordamp.ikonli.material2.Material2AL;
import org.kordamp.ikonli.octicons.Octicons;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class DocumentEditor extends MonacoEditorView {

  private final IconButton saveButton;
  private final Label status;
  private final NavigableKlabDocument<?, ?> document;

  public DocumentEditor(
      NavigableKlabDocument<?, ?> document, String documentUri, Consumer<String> saveCallback) {
    super(
        "inmemory:///klab/" + document.getUrn() + "." + document.getLanguage().fileExtension(),
        saveCallback);
    this.document = document;
    this.saveButton =
        IconButton.of(Codicons.SAVE, 12, Theme.FOREGROUND_COLOR, Theme.FOREGROUND_COLOR, null);
    saveButton.setTooltip(new Tooltip("Save"));
    this.status = new Label("Ready");
  }

  @Override
  protected Collection<MonacoEditorView.BarComponent> createHeaderBarComponents() {

    var lineNumbers =
        IconButton.toggle(
            Material2AL.FORMAT_LIST_NUMBERED,
            12,
            () -> {
              this.setLineNumbers(!this.isLineNumbersVisible());
              return this.isLineNumbersVisible();
            });
    lineNumbers.setToggled(true);
    var minimap =
        IconButton.toggle(
            BootstrapIcons.LAYOUT_SIDEBAR_REVERSE,
            12,
            () -> {
              this.setMinimapVisible(!this.isMinimapVisible());
              return this.isMinimapVisible();
            });
    lineNumbers.setToggled(this.isLineNumbersVisible());
    minimap.setToggled(this.isMinimapVisible());
    lineNumbers.setTooltip(new Tooltip("Toggle line numbers"));
    minimap.setTooltip(new Tooltip("Toggle the minimap"));

    var reviewMode =
        IconButton.toggle(
            Octicons.CODE_REVIEW_24,
            12,
            () -> {
              return toggleReviewMode();
            });

    reviewMode.setToggled(this.isReviewMode());
    // TODO enable only if the doc has some public flow or if user can
    //  open a review
    reviewMode.setTooltip(new Tooltip("Toggle review mode"));

    return List.of(
        new MonacoEditorView.BarComponent(saveButton, MonacoEditorView.BarSide.LEFT),
        new MonacoEditorView.BarComponent(reviewMode, MonacoEditorView.BarSide.LEFT),
        new MonacoEditorView.BarComponent(lineNumbers, MonacoEditorView.BarSide.RIGHT),
        new MonacoEditorView.BarComponent(minimap, MonacoEditorView.BarSide.RIGHT));
  }

  public boolean toggleReviewMode() {
    setReviewMode(!isReviewMode());
    return isReviewMode();
  }

  @Override
  protected Collection<MonacoEditorView.BarComponent> createStatusBarComponents() {

    return List.of(new MonacoEditorView.BarComponent(status, MonacoEditorView.BarSide.LEFT));
  }
}

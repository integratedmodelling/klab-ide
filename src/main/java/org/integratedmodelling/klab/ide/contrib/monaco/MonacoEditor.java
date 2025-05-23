/*
 * MIT License
 *
 * Copyright (c) 2020-2022 Michael Hoffer <info@michaelhoffer.de>. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.integratedmodelling.klab.ide.contrib.monaco;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2AL;

public class MonacoEditor extends BorderPane {

  private final WebView view;
  private final WebEngine engine;

  private static final String EDITOR_HTML_RESOURCE_LOCATION =
      "/org/integratedmodelling/monaco/monaco-editor-0.20.0/index.html";

  private final Editor editor;
  private final SystemClipboardWrapper systemClipboardWrapper;

  public MonacoEditor(final Consumer<MonacoEditor> configurer) {

    setTop(createMenuBar());
    view = new WebView();
    setCenter(view);
    engine = view.getEngine();
    String url = getClass().getResource(EDITOR_HTML_RESOURCE_LOCATION).toExternalForm();

    engine.load(url);

    editor = new Editor(engine);

    systemClipboardWrapper = new SystemClipboardWrapper();
    ClipboardBridge clipboardBridge =
        new ClipboardBridge(editor.getDocument(), systemClipboardWrapper);
    engine
        .getLoadWorker()
        .stateProperty()
        .addListener(
            (o, old, state) -> {
              if (state == Worker.State.SUCCEEDED) {

                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("clipboardBridge", clipboardBridge);

                AtomicBoolean jsDone = new AtomicBoolean(false);
                AtomicInteger attempts = new AtomicInteger();

                Thread thread =
                    new Thread(
                        () -> {
                          while (!jsDone.get()) {
                            try {
                              Thread.sleep(500);
                            } catch (InterruptedException e) {
                              e.printStackTrace();
                            }
                            // check if JS execution is done.
                            Platform.runLater(
                                () -> {
                                  Object jsEditorObj = window.call("getEditorView");
                                  if (jsEditorObj instanceof JSObject) {
                                    editor.setEditor(window, (JSObject) jsEditorObj);
                                    jsDone.set(true);
                                    if (configurer != null) {
                                      configurer.accept(this);
                                    }
                                  }
                                });

                            if (attempts.getAndIncrement() > 10) {
                              throw new RuntimeException(
                                  "Cannot initialize editor (JS execution not complete). Max number of attempts reached.");
                            }
                          }
                        });
                thread.start();
              }
            });

    addEventFilter(
        KeyEvent.KEY_PRESSED,
        event -> {
          Object obj =
              engine.executeScript(
                  "editorView.getModel().getValueInRange(editorView.getSelection())");
          systemClipboardWrapper.handleCopyCutKeyEvent(event, obj);
        });
  }

  public HBox createMenuBar() {

    // FIXME for some reason this does not show

    var left = new HBox();
    left.setAlignment(Pos.CENTER_LEFT);
    var right = new HBox();
    right.setAlignment(Pos.CENTER_RIGHT);
    var button = new Button("", new FontIcon(Material2AL.CHROME_READER_MODE));
    button.setOnMouseClicked(
        e -> {
          editor.getViewController().toggleMinibar();
        });
    right.getChildren().add(button);
    return new HBox(left, right);
  }

  @Override
  protected double computePrefWidth(double height) {
    return view.prefWidth(height);
  }

  @Override
  protected double computePrefHeight(double width) {
    return view.prefHeight(width);
  }

  @Override
  public void requestLayout() {
    super.requestLayout();
  }

  @Override
  protected void layoutChildren() {
    super.layoutChildren();

    layoutInArea(view, 0, 0, getWidth(), getHeight(), 0, HPos.CENTER, VPos.CENTER);
  }

  @Override
  public void requestFocus() {
    view.requestFocus();
  }

  public Editor getEditor() {
    return editor;
  }

  @Deprecated
  public WebEngine getWebEngine() {
    return engine;
  }
}

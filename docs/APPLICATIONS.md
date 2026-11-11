# Run project applications

[Documentation home](../README.md) · [Behaviors and agents](BEHAVIORS.md)

Project applications targeting `WEB` or `ANY` can run in an Application tab inside
the IDE or in a browser. You need a connected local Runtime at a local address and
a saved application in a project. Desktop/mobile applications keep their source
editor but do not use this web runner.

## Launch a saved application

1. Find the application in the workspace tree.
2. Choose **Edit and run application locally** to open its managed source copy.
3. Choose a destination using the browser icon beside Debug/Run: **Internal
   WebView**, the default browser, or a detected installed browser.
4. Select Run. The Application tab provides **Run**, **Stop**, and **Reconnect**
   controls and a connection-status console.

Use **Choose browser…** to locate another browser executable. You do not need a
separate Node.js installation to use the packaged renderer. Forms, choices,
sliders, navigation, Markdown, images, media, and file upload/download use the
application's supported widgets. Custom toolkits need their own runtime renderer;
media codec support depends on the selected browser engine.

> **Screenshot placeholder — Application runner:** show a saved project application,
> browser chooser, rendered form, Run/Stop/Reconnect controls, and connection console.

## Save and rerun changes

The runner launches the **saved project application**. Saving in a workspace source
editor updates that project directly. Saving a managed local mirror updates only
the mirror until you publish/synchronize it back to the project.

After updating project source, select **Stop**, then **Run saved application**.
Existing runs retain the source they started with. Reconnect preserves the same
instance and its retained state; it does not publish source or restart execution.
For a standalone file, publish it to a project first and reopen it from that project.

## Change renderer or reconnect

The running application's **Open in** choice shares the source editor's browser
setting. Changing destinations during a run reconnects the renderer to the same
instance. Reloading an external browser resumes its current view. **Reconnect**
can reopen the chosen browser after a connection interruption.

Keep the IDE running while using an external browser: it owns the connection to
the local runtime. Closing the browser window leaves the application active.
Use **Stop**, or close the application/editor tab, to request termination.

The web runner's console reports launch and transport state. It does not attach
the ordinary agent debugger. See [Troubleshooting](TROUBLESHOOTING.md) if launch,
reconnection, or a widget fails.

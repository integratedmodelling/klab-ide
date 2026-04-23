package org.integratedmodelling.klab.ide;

import java.util.function.Supplier;
import org.integratedmodelling.common.commandline.KlabCommandLine;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.ide.test.AutoScrollDemoViewComponent;
import org.integratedmodelling.klab.ide.test.TimelineDemoViewComponent;

/**
 * Instrumented command line that will also add demo components or other JavaFX components for
 * display.
 */
public class ModelerCommandLine extends KlabCommandLine {

  public ModelerCommandLine(Supplier<Scope> scopeSupplier) {
    super(scopeSupplier);

    command("demo", "Demonstrate UI components", "Create views to demonstrate local UI components")
        .subCommand("timeline", "Timeline component", "Show an example timeline")
        .handler(commandLine -> new TimelineDemoViewComponent())
        .parent()
        .subCommand(
            "autoscroll",
            "Autoscroll component",
            "Show examples of the autoscrolling list component")
        .handler(commandLine -> new AutoScrollDemoViewComponent())
        .parent()
        .build();
  }
}

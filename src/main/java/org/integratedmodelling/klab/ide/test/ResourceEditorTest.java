package org.integratedmodelling.klab.ide.test;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.integratedmodelling.klab.ide.components.ResourceEditor;

/**
 * Simple test class to display the ResourceEditor component
 */
public class ResourceEditorTest extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Create the ResourceEditor with null asset (just to show the UI)
        ResourceEditor editor = new ResourceEditor(null);
        
        // Create a scene with the editor
        Scene scene = new Scene(editor, 1024, 768);
        
        // Set up the stage
        primaryStage.setTitle("Resource Editor Test");
        primaryStage.setScene(scene);
        primaryStage.show();
        
        // No need to explicitly show content, the editor will display itself
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
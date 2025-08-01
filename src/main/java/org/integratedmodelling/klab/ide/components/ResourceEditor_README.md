# ResourceEditor Component

This JavaFX component provides a user interface for viewing and editing Resource objects in the k.LAB IDE. It was designed to be similar to the original SWT-based ResourceEditor.

## Features

The ResourceEditor provides the following features:

- Display and edit resource metadata
- Configure resource geometry (space and time)
- Manage resource attributes, inputs, and outputs
- Configure adapter parameters
- Execute operations on resources

## Usage

To use the ResourceEditor in your application:

```java
// Create a ResourceEditor with a Resource object
ResourceEditor editor = new ResourceEditor(resourceObject);

// Add it to your scene
myContainer.getChildren().add(editor);

// To edit a specific resource
editor.edit(resourceObject);
```

## UI Structure

The ResourceEditor is organized into several tabs:

1. **Resource Data** - Contains basic resource information, geometry configuration, and adapter parameters
   - Resource URN and local name
   - Space and time configuration
   - Attributes, inputs, and outputs tables
   - Adapter parameters tree

2. **Metadata** - Contains fields for resource metadata
   - Title
   - Description
   - Keywords
   - Version
   - License

3. **Provenance** - Contains fields for resource provenance information
   - Creator
   - Organization
   - URL
   - Creation date

4. **Operations** - Provides access to operations that can be performed on the resource
   - List of available operations
   - Operation parameters
   - Execution controls

## Testing

A simple test application is provided in `org.integratedmodelling.klab.ide.test.ResourceEditorTest`. This application creates a ResourceEditor and displays it in a window.

## Implementation Notes

- The ResourceEditor extends the EditorPage class, which provides the basic structure for editors in the k.LAB IDE.
- The UI is built using JavaFX components and follows the design patterns used in the rest of the k.LAB IDE.
- The component is designed to be responsive and will adjust to different window sizes.
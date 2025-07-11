package org.integratedmodelling.klab.ide.components;

import javafx.scene.Node;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.view.modeler.navigation.NavigableAsset;
import org.integratedmodelling.klab.ide.pages.EditorPage;

public class ResourceEditor extends EditorPage<Object, Resource> {

    public ResourceEditor(Object asset) {
        super(asset);
    }

    @Override
    protected TreeView<Resource> createContentTree() {
        var ret = new TreeView<Resource>();
        return ret;
    }

    @Override
    protected void onSingleClickItemSelection(Resource value) {

    }

    @Override
    protected void onDoubleClickItemSelection(Resource value) {

    }

    @Override
    protected Node createEditor(Resource asset) {
        return null;
    }

}

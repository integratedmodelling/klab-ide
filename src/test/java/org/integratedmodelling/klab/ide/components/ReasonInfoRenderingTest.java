package org.integratedmodelling.klab.ide.components;

import static org.junit.jupiter.api.Assertions.*;

import org.integratedmodelling.klab.api.cli.MarkdownDocument;
import org.junit.jupiter.api.Test;

class ReasonInfoRenderingTest {
  @Test void rendersSemanticHeadingsAndKeepsMetadataTagsInert() {
    var document = new MarkdownDocument("# Semantic documentation\n\n## Inherited restrictions\n\n- `of`: `audit:Tree`\n- Metadata: `[b]literal[/b]`\n");
    var bbCode = org.integratedmodelling.klab.ide.utils.BBCodeNodeRenderer.fromMarkdown(document.content());
    assertTrue(bbCode.contains("[heading=1]Semantic documentation[/heading]"));
    assertTrue(bbCode.contains("Inherited restrictions"));
    assertTrue(bbCode.contains("audit:Tree"));
    assertFalse(bbCode.contains("[b]literal[/b]"));
  }
}
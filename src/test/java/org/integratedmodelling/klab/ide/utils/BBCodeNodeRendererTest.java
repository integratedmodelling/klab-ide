package org.integratedmodelling.klab.ide.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import atlantafx.base.util.BBCodeHandler;
import atlantafx.base.util.BBCodeParser;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import org.integratedmodelling.common.utils.Utils;
import org.junit.jupiter.api.Test;

class BBCodeNodeRendererTest {

  @Test
  void rendersCoreMarkdownAsAtlantaFxBbCode() {
    String markdown =
        """
        # Heading

        Plain *italic*, **bold**, `code`, and [link](https://example.com/).

        > Quoted text

        3. three
        4. four

        - one
        - two

        ---

        ```
        value = [b]not markup[/b]
        ```
        """;

    String bbCode = render(markdown);

    assertTrue(bbCode.contains("[heading=1]Heading[/heading]"));
    assertTrue(bbCode.contains("[i]italic[/i]"));
    assertTrue(bbCode.contains("[b]bold[/b]"));
    assertTrue(bbCode.contains("[code]code[/code]"));
    assertTrue(bbCode.contains("[url=\"https://example.com/\"]link[/url]"));
    assertTrue(bbCode.contains("[indent]Quoted text[/indent]"));
    assertTrue(bbCode.contains("[ol=3]"));
    assertTrue(bbCode.contains("[ul]"));
    assertTrue(bbCode.contains("[hr/]"));
    assertTrue(bbCode.contains("[code]value = [\u2060b]not markup[\u2060/b]\n[/code]"));
    assertFalse(bbCode.contains("<h1>"));
    assertValidBbCode(bbCode);
  }

  @Test
  void rendersReferenceLinksAndImagesWithoutUnsupportedImageTags() {
    String bbCode =
        render(
            """
            [site][docs] and ![diagram][image]

            [docs]: https://example.com/docs
            [image]: https://example.com/image.png
            """);

    assertTrue(bbCode.contains("[url=\"https://example.com/docs\"]site[/url]"));
    assertTrue(bbCode.contains("[url=\"https://example.com/image.png\"]diagram[/url]"));
    assertFalse(bbCode.contains("[img"));
    assertValidBbCode(bbCode);
  }

  private static String render(String markdown) {
    return Utils.Markdown.render(markdown, new BBCodeNodeRenderer());
  }

  private static void assertValidBbCode(String bbCode) {
    Deque<String> tags = new ArrayDeque<>();
    new BBCodeParser(
            bbCode,
            new BBCodeHandler() {
              @Override
              public void startDocument(char[] document) {}

              @Override
              public void endDocument() {}

              @Override
              public void startTag(
                  String name, Map<String, String> parameters, int start, int length) {
                if (!"hr".equals(name)) {
                  tags.push(name);
                }
              }

              @Override
              public void endTag(String name, int start, int length) {
                assertEquals(tags.pop(), name);
              }

              @Override
              public void characters(int start, int length) {}
            })
        .parse();
    assertTrue(tags.isEmpty());
  }
}

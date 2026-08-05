package org.integratedmodelling.klab.ide.utils;

import com.vladsch.flexmark.ast.*;
import com.vladsch.flexmark.html.HtmlWriter;
import com.vladsch.flexmark.html.renderer.LinkType;
import com.vladsch.flexmark.html.renderer.NodeRenderer;
import com.vladsch.flexmark.html.renderer.NodeRendererContext;
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler;
import com.vladsch.flexmark.html.renderer.ResolvedLink;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.TextCollectingVisitor;
import com.vladsch.flexmark.util.sequence.Escaping;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Renders Flexmark's core Markdown nodes using the BBCode understood by AtlantaFX. */
public class BBCodeNodeRenderer implements NodeRenderer {

  /* A word joiner makes a source BBCode tag inert without changing how the text is displayed. */
  private static final String INERT_TAG_PREFIX = "[\u2060";
  private static final Pattern BB_CODE_TAG =
      Pattern.compile("\\[(?=/?[A-Za-z][A-Za-z0-9]*(?:\\s|=|/|\\]))");

  @Override
  public Set<NodeRenderingHandler<?>> getNodeRenderingHandlers() {
    return new HashSet<>(
        List.of(
            new NodeRenderingHandler<>(AutoLink.class, this::render),
            new NodeRenderingHandler<>(BlockQuote.class, this::render),
            new NodeRenderingHandler<>(BulletList.class, this::render),
            new NodeRenderingHandler<>(BulletListItem.class, this::render),
            new NodeRenderingHandler<>(Code.class, this::render),
            new NodeRenderingHandler<>(CodeBlock.class, this::render),
            new NodeRenderingHandler<>(Document.class, this::render),
            new NodeRenderingHandler<>(Emphasis.class, this::render),
            new NodeRenderingHandler<>(FencedCodeBlock.class, this::render),
            new NodeRenderingHandler<>(HardLineBreak.class, this::render),
            new NodeRenderingHandler<>(Heading.class, this::render),
            new NodeRenderingHandler<>(HtmlBlock.class, this::render),
            new NodeRenderingHandler<>(HtmlCommentBlock.class, this::render),
            new NodeRenderingHandler<>(HtmlInnerBlock.class, this::render),
            new NodeRenderingHandler<>(HtmlInnerBlockComment.class, this::render),
            new NodeRenderingHandler<>(HtmlEntity.class, this::render),
            new NodeRenderingHandler<>(HtmlInline.class, this::render),
            new NodeRenderingHandler<>(HtmlInlineComment.class, this::render),
            new NodeRenderingHandler<>(Image.class, this::render),
            new NodeRenderingHandler<>(ImageRef.class, this::render),
            new NodeRenderingHandler<>(IndentedCodeBlock.class, this::render),
            new NodeRenderingHandler<>(Link.class, this::render),
            new NodeRenderingHandler<>(LinkRef.class, this::render),
            new NodeRenderingHandler<>(MailLink.class, this::render),
            new NodeRenderingHandler<>(OrderedList.class, this::render),
            new NodeRenderingHandler<>(OrderedListItem.class, this::render),
            new NodeRenderingHandler<>(Paragraph.class, this::render),
            new NodeRenderingHandler<>(Reference.class, this::render),
            new NodeRenderingHandler<>(SoftLineBreak.class, this::render),
            new NodeRenderingHandler<>(StrongEmphasis.class, this::render),
            new NodeRenderingHandler<>(Text.class, this::render),
            new NodeRenderingHandler<>(TextBase.class, this::render),
            new NodeRenderingHandler<>(ThematicBreak.class, this::render)));
  }

  private void render(Document node, NodeRendererContext context, HtmlWriter html) {
    context.renderChildren(node);
  }

  private void render(Heading node, NodeRendererContext context, HtmlWriter html) {
    open(html, "heading=" + node.getLevel());
    context.renderChildren(node);
    close(html, "heading");
    html.raw("\n\n");
  }

  private void render(BlockQuote node, NodeRendererContext context, HtmlWriter html) {
    open(html, "indent");
    context.renderChildren(node);
    close(html, "indent");
    html.raw("\n\n");
  }

  private void render(FencedCodeBlock node, NodeRendererContext context, HtmlWriter html) {
    renderCodeBlock(node.getContentChars().normalizeEOL(), html);
  }

  private void render(IndentedCodeBlock node, NodeRendererContext context, HtmlWriter html) {
    renderCodeBlock(node.getContentChars().trimTailBlankLines().normalizeEOL(), html);
  }

  private void render(CodeBlock node, NodeRendererContext context, HtmlWriter html) {
    rawText(node.getContentChars().normalizeEOL(), html);
  }

  private void renderCodeBlock(CharSequence code, HtmlWriter html) {
    open(html, "code");
    rawText(code, html);
    close(html, "code");
    html.raw("\n\n");
  }

  private void render(BulletList node, NodeRendererContext context, HtmlWriter html) {
    renderContainer(node, context, html, "ul");
  }

  private void render(OrderedList node, NodeRendererContext context, HtmlWriter html) {
    String tag = node.getStartNumber() == 1 ? "ol" : "ol=" + node.getStartNumber();
    renderContainer(node, context, html, tag);
  }

  private void renderContainer(
      Node node, NodeRendererContext context, HtmlWriter html, String openingTag) {
    open(html, openingTag);
    html.raw("\n");
    context.renderChildren(node);
    close(html, tagName(openingTag));
    html.raw("\n\n");
  }

  private void render(BulletListItem node, NodeRendererContext context, HtmlWriter html) {
    renderListItem(node, context, html);
  }

  private void render(OrderedListItem node, NodeRendererContext context, HtmlWriter html) {
    renderListItem(node, context, html);
  }

  private void renderListItem(ListItem node, NodeRendererContext context, HtmlWriter html) {
    open(html, "li");
    context.renderChildren(node);
    close(html, "li");
    html.raw("\n");
  }

  private void render(Paragraph node, NodeRendererContext context, HtmlWriter html) {
    context.renderChildren(node);
    if (node.getParent() instanceof Document || node.getNext() != null) {
      html.raw("\n\n");
    }
  }

  private void render(SoftLineBreak node, NodeRendererContext context, HtmlWriter html) {
    html.raw("\n");
  }

  private void render(HardLineBreak node, NodeRendererContext context, HtmlWriter html) {
    html.raw("\n");
  }

  private void render(Emphasis node, NodeRendererContext context, HtmlWriter html) {
    renderInline(node, context, html, "i");
  }

  private void render(StrongEmphasis node, NodeRendererContext context, HtmlWriter html) {
    renderInline(node, context, html, "b");
  }

  private void renderInline(Node node, NodeRendererContext context, HtmlWriter html, String tag) {
    open(html, tag);
    context.renderChildren(node);
    close(html, tag);
  }

  private void render(Text node, NodeRendererContext context, HtmlWriter html) {
    rawText(Escaping.normalizeEOL(node.getChars().unescape()), html);
  }

  private void render(TextBase node, NodeRendererContext context, HtmlWriter html) {
    context.renderChildren(node);
  }

  private void render(Code node, NodeRendererContext context, HtmlWriter html) {
    open(html, "code");
    rawText(Escaping.collapseWhitespace(node.getText(), true), html);
    close(html, "code");
  }

  private void render(ThematicBreak node, NodeRendererContext context, HtmlWriter html) {
    html.raw("[hr/]\n\n");
  }

  private void render(HtmlBlock node, NodeRendererContext context, HtmlWriter html) {
    rawText(node.getContentChars().normalizeEOL(), html);
    html.raw("\n\n");
  }

  private void render(HtmlInnerBlock node, NodeRendererContext context, HtmlWriter html) {
    rawText(node.getChars().normalizeEOL(), html);
  }

  private void render(HtmlInline node, NodeRendererContext context, HtmlWriter html) {
    rawText(node.getChars().normalizeEOL(), html);
  }

  private void render(HtmlCommentBlock node, NodeRendererContext context, HtmlWriter html) {}

  private void render(HtmlInnerBlockComment node, NodeRendererContext context, HtmlWriter html) {}

  private void render(HtmlInlineComment node, NodeRendererContext context, HtmlWriter html) {}

  private void render(HtmlEntity node, NodeRendererContext context, HtmlWriter html) {
    rawText(node.getChars().unescape(), html);
  }

  private void render(AutoLink node, NodeRendererContext context, HtmlWriter html) {
    String text = node.getText().toString();
    String url = node.getUrl().unescape();
    if (url.startsWith("www.")) {
      url = context.getHtmlOptions().autolinkWwwPrefix + url;
    }
    renderLink(text, url, LinkType.LINK, context, html);
  }

  private void render(MailLink node, NodeRendererContext context, HtmlWriter html) {
    String address = node.getText().unescape();
    if (context.isDoNotRenderLinks() || isSuppressed(address, context)) {
      rawText(address, html);
      return;
    }
    ResolvedLink link = context.resolveLink(LinkType.LINK, address, null);
    open(html, "email=\"" + parameter(link.getUrl()) + "\"");
    rawText(address, html);
    close(html, "email");
  }

  private void render(Link node, NodeRendererContext context, HtmlWriter html) {
    renderResolvedLink(node.getUrl().unescape(), node, context, html);
  }

  private void render(LinkRef node, NodeRendererContext context, HtmlWriter html) {
    Reference reference = findReference(node, node.getReference().unescape());
    if (reference == null) {
      rawText(node.getChars().unescape(), html);
    } else {
      renderResolvedLink(reference.getUrl().unescape(), node, context, html);
    }
  }

  private void renderResolvedLink(
      String url, Node node, NodeRendererContext context, HtmlWriter html) {
    if (context.isDoNotRenderLinks() || isSuppressed(url, context)) {
      context.renderChildren(node);
      return;
    }
    ResolvedLink link = context.resolveLink(LinkType.LINK, url, null, null);
    open(html, "url=\"" + parameter(link.getUrl()) + "\"");
    context.renderChildren(node);
    close(html, "url");
  }

  private void render(Image node, NodeRendererContext context, HtmlWriter html) {
    renderImageLink(node.getUrl().unescape(), node, context, html);
  }

  private void render(ImageRef node, NodeRendererContext context, HtmlWriter html) {
    Reference reference = findReference(node, node.getReference().unescape());
    if (reference == null) {
      rawText(node.getChars().unescape(), html);
    } else {
      renderImageLink(reference.getUrl().unescape(), node, context, html);
    }
  }

  private void renderImageLink(
      String url, Node node, NodeRendererContext context, HtmlWriter html) {
    String altText = new TextCollectingVisitor().collectAndGetText(node);
    ResolvedLink link = context.resolveLink(LinkType.IMAGE, url, null, null);
    if (context.isDoNotRenderLinks() || isSuppressed(url, context)) {
      rawText(altText, html);
      return;
    }
    open(html, "url=\"" + parameter(link.getUrl()) + "\"");
    rawText(altText.isBlank() ? link.getUrl() : altText, html);
    close(html, "url");
  }

  private void render(Reference node, NodeRendererContext context, HtmlWriter html) {}

  private void renderLink(
      String text, String url, LinkType linkType, NodeRendererContext context, HtmlWriter html) {
    if (context.isDoNotRenderLinks() || isSuppressed(url, context)) {
      rawText(text, html);
      return;
    }
    ResolvedLink link = context.resolveLink(linkType, url, null);
    open(html, "url=\"" + parameter(link.getUrl()) + "\"");
    rawText(text, html);
    close(html, "url");
  }

  private static boolean isSuppressed(CharSequence url, NodeRendererContext context) {
    Pattern pattern = context.getHtmlOptions().suppressedLinks;
    return pattern != null && pattern.matcher(url).matches();
  }

  private static void rawText(CharSequence text, HtmlWriter html) {
    html.raw(BB_CODE_TAG.matcher(text).replaceAll(INERT_TAG_PREFIX));
  }

  private static String parameter(String value) {
    return value
        .replace("[", "%5B")
        .replace("]", "%5D")
        .replace("\"", "%22")
        .replace("'", "%27")
        .replace("\r", "")
        .replace("\n", "%0A");
  }

  private static Reference findReference(Node node, String label) {
    String normalizedLabel = normalizeReference(label);
    for (Node child = node.getDocument().getFirstChild(); child != null; child = child.getNext()) {
      if (child instanceof Reference reference
          && normalizeReference(reference.getReference().unescape()).equals(normalizedLabel)) {
        return reference;
      }
    }
    return null;
  }

  private static String normalizeReference(String label) {
    return label.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private static void open(HtmlWriter html, String tag) {
    html.raw("[").raw(tag).raw("]");
  }

  private static void close(HtmlWriter html, String tag) {
    html.raw("[/").raw(tag).raw("]");
  }

  private static String tagName(String tag) {
    int equals = tag.indexOf('=');
    return equals < 0 ? tag : tag.substring(0, equals);
  }
}

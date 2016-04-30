/*
 *  Commonmark Lib
 *  Copyright (C) 2016  Nikolay Platov
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nikoladasm.commonmark;

import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import nikoladasm.commonmark.nodes.*;

public class HtmlRenderer {

	@FunctionalInterface
	private static interface Renderer {
		void render(Node node);
	}
	
	private String softbreak;
	private Map<Class<? extends Node>,Renderer> renderers;
	private Map<Class<? extends Node>,Renderer> closingTagRenderers;
	private StringBuilder sb;
	private String lastOut;
	private boolean safe;
	private boolean sourcepos;
	private int disableTags;
	private Map<String,String> attr = new HashMap<>();
	private StringBuilder tmp;
	
	public HtmlRenderer() {
		softbreak = "\n";
		lastOut = "\n";
		safe = false;
		disableTags = 0;
		renderers = new HashMap<>();
		renderers.put(InlineTextNode.class, this::inlineText);
		renderers.put(InlineSoftBreakNode.class, this::softBreak);
		renderers.put(InlineLineBreakNode.class, this::lineBreak);
		renderers.put(InlineLinkNode.class, this::openLink);
		renderers.put(InlineImageNode.class, this::openImage);
		renderers.put(InlineEmphasisNode.class, this::openEmphasis);
		renderers.put(InlineStrongEmphasisNode.class, this::openStrongEmphasis);
		renderers.put(InlineHtmlNode.class, this::inlineHtml);
		renderers.put(InlineCodeNode.class, this::inlineCode);
		renderers.put(ParagraphBlockNode.class, this::openParagraph);
		renderers.put(HeadingBlockNode.class, this::openHeading);
		renderers.put(CodeBlockNode.class, this::codeBlock);
		renderers.put(ThematicBreakNode.class, this::thematicBreak);
		renderers.put(BlockQuoteNode.class, this::openBlockQuote);
		renderers.put(ListBlockNode.class, this::openList);
		renderers.put(ItemBlockNode.class, this::openItem);
		renderers.put(HtmlBlockNode.class, this::htmlBlock);
		closingTagRenderers = new HashMap<>();
		closingTagRenderers.put(InlineLinkNode.class, this::closeLink);
		closingTagRenderers.put(InlineImageNode.class, this::closeImage);
		closingTagRenderers.put(InlineEmphasisNode.class, this::closeEmphasis);
		closingTagRenderers.put(InlineStrongEmphasisNode.class, this::closeStrongEmphasis);
		closingTagRenderers.put(ParagraphBlockNode.class, this::closeParagraph);
		closingTagRenderers.put(HeadingBlockNode.class, this::closeHeading);
		closingTagRenderers.put(BlockQuoteNode.class, this::closeBlockQuote);
		closingTagRenderers.put(ListBlockNode.class, this::closeList);
		closingTagRenderers.put(ItemBlockNode.class, this::closeItem);
	}
	
	public String softbreak() {
		return softbreak;
	}
	
	public void softbreak(String softbreak) {
		this.softbreak = softbreak;
	}
	
	public boolean isSafe() {
		return safe;
	}
	
	public void safe(boolean safe) {
		this.safe = safe;
	}
	
	public boolean isSourcepos() {
		return sourcepos;
	}
	
	public void sourcepos(boolean sourcepos) {
		this.sourcepos = sourcepos;
	}
	
	public String render(Node ast) {
		if (ast == null) return null;
		lastOut = "\n";
		Node current = ast;
		Deque<Iterator<Node>> stack = new LinkedList<>();
		Deque<Node> nodeStack = new LinkedList<>();
		boolean finished = false;
		sb = new StringBuilder();
		tmp = new StringBuilder();
		while (!finished) {
			while (current != null) {
				Class<? extends Node> clazz = current.getClass();
				Renderer renderer = renderers.get(clazz);
				if (renderer != null) {
					attr.clear();
					if (sourcepos && current instanceof BlockNode) {
						BlockNode bNode = (BlockNode) current;
						tmp.setLength(0);
						attr.put("data-sourcepos",
							tmp.append(bNode.startLine())
							.append(':').append(bNode.startColumn())
							.append('-').append(bNode.endLine())
							.append(':').append(bNode.endColumn())
							.toString());
					}
					renderer.render(current);
				}
				if (current.childs().isEmpty()) {
					renderer = closingTagRenderers.get(clazz);
					if (renderer != null) renderer.render(current);
					break;
				}
				Iterator<Node> iterator = current.childs().iterator();
				stack.push(iterator);
				nodeStack.push(current);
				current = iterator.next();
			}
			Iterator<Node> iterator = stack.peek();
			if (iterator != null && iterator.hasNext()) {
				current = iterator.next();
			} else if (iterator == null) {
				finished = true;
				break;
			} else {
				stack.pop();
				Node node = nodeStack.pop();
				Renderer renderer = closingTagRenderers.get(node.getClass());
				if (renderer != null) renderer.render(node);
				current = null;
			}
		}
		return sb.toString();
	}
	
	private String esc(String str) {
		StringBuilder sb = new StringBuilder();
		for (int i=0; i < str.length(); i++) {
			char c = str.charAt(i);
			switch (c) {
				case '&' :
					sb.append("&amp;");
					break;
				case '<' :
					sb.append("&lt;");
					break;
				case '>' :
					sb.append("&gt;");
					break;
				case '"' :
					sb.append("&quot;");
					break;
				default :
					sb.append(c);
					break;
			}
		}
		return sb.toString();
	}
	
	private void inlineText(Node node) {
		String str = ((InlineTextNode) node).literal();
		sb.append(esc(str));
		lastOut = str;
	}
	
	private void softBreak(Node node) {
		sb.append(softbreak);
		lastOut = softbreak;
	}
	
	private void cr() {
		if (!"\n".equals(lastOut)) {
			sb.append('\n');
			lastOut = "\n";
		}
	}
	
	private void addTag(String tag, Map<String,String> attr, boolean selfclosing) {
		if (disableTags > 0) return;
		sb.append('<').append(tag);
		if (attr != null)
			attr.forEach((key, value) ->
				sb.append(' ').append(key).append("=\"").append(value).append('"'));
		if (selfclosing) sb.append(" /");
		sb.append('>');
		lastOut = ">";
	}
	
	private void lineBreak(Node node) {
		addTag("br", attr.isEmpty() ? node.attr() : attr, true);
		cr();
	}
	
	private boolean potentiallyUnsafe(String url) {
		url = url.toLowerCase();
		return (url.startsWith("javascript:") ||
			url.startsWith("vbscript:") ||
			url.startsWith("file:") ||
			url.startsWith("data:")) &&
			!(url.startsWith("data:image/png") ||
			url.startsWith("data:image/gif") ||
			url.startsWith("data:image/jpeg") ||
			url.startsWith("data:image/webp"));
	}
	
	private void openLink(Node node) {
		String reference = ((InlineLinkNode) node).reference();
		String title = ((InlineLinkNode) node).title();
		if (!(safe && potentiallyUnsafe(reference)))
			attr.put("href", esc(reference));
		if (!title.isEmpty())
			attr.put("title", esc(title));
		if (!attr.isEmpty()) attr.putAll(node.attr());
		addTag("a", attr.isEmpty() ? node.attr() : attr, false);
	}
	
	private void closeLink(Node node) {
		addTag("/a", null, false);
	}
	
	private void openImage(Node node) {
		String reference = ((InlineImageNode) node).reference();
		if (disableTags == 0) {
			if (safe && potentiallyUnsafe(reference))
				sb.append("<img src=\"\" alt=\"");
			else
				sb.append("<img src=\"").append(esc(reference)).append("\" alt=\"");
			lastOut = "\"";
		}
		disableTags++;
	}
	
	private void closeImage(Node node) {
		disableTags--;
		String title = ((InlineImageNode) node).title();
		if (this.disableTags == 0) {
			if (!title.isEmpty())
				sb.append("\" title=\"").append(esc(title));
			Map<String,String> attr = this.attr.isEmpty() ? node.attr() : this.attr;
			if (attr.isEmpty()) {
				sb.append("\" />");
			} else {
				sb.append("\" ");
				attr.forEach((key, value) ->
					sb.append(' ').append(key).append("=\"").append(value).append('"'));
				sb.append(" />");
			}
			lastOut = "\" />";
		}
	}
	
	private void openEmphasis(Node node) {
		addTag("em", attr.isEmpty() ? node.attr() : attr, false);
	}
	
	private void closeEmphasis(Node node) {
		addTag("/em", null, false);
	}

	private void openStrongEmphasis(Node node) {
		addTag("strong", attr.isEmpty() ? node.attr() : attr, false);
	}
	
	private void closeStrongEmphasis(Node node) {
		addTag("/strong", null, false);
	}
	
	private void inlineCode(Node node) {
		String str = ((InlineCodeNode) node).literal();
		addTag("code", attr.isEmpty() ? node.attr() : attr, false);
		sb.append(esc(str));
		addTag("/code", null, false);
	}
	
	private void inlineHtml(Node node) {
		if (safe)
			sb.append("<!-- raw HTML omitted -->");
		else
			sb.append(((InlineHtmlNode) node).literal());
		lastOut = ">";
	}
	
	private void openParagraph(Node node) {
		Node grandparent = node.parent().parent();
		if (grandparent != null &&
			(grandparent instanceof ListBlockNode) &&
			((ListBlockNode) grandparent).tight())
			return;
		cr();
		addTag("p", attr.isEmpty() ? node.attr() : attr, false);
	}
	
	private void closeParagraph(Node node) {
		Node grandparent = node.parent().parent();
		if (grandparent != null &&
			(grandparent instanceof ListBlockNode) &&
			((ListBlockNode) grandparent).tight())
			return;
		addTag("/p", null, false);
		cr();
	}
	
	private void openHeading(Node node) {
		String tagname = "h" + ((HeadingBlockNode) node).level();
		cr();
		addTag(tagname, attr.isEmpty() ? node.attr() : attr, false);
	}
	
	private void closeHeading(Node node) {
		String tagname = "/h" + ((HeadingBlockNode) node).level();
		addTag(tagname, null, false);
		cr();
	}
	
	private void codeBlock(Node node) {
		CodeBlockNode cNode = (CodeBlockNode) node;
		String info = cNode.info();
		if (!info.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			int length = info.length();
			char c;
			for (int i = 0; i < length; i++) {
				c = info.charAt(i);
				if (" \t\n\u000B\f\r".indexOf(c) >= 0) break;
				sb.append(c);
			}
			if (sb.length() > 0)
				attr.put("class", "language-" + esc(sb.toString()));
		}
		if (!attr.isEmpty()) attr.putAll(cNode.attr());
		cr();
		addTag("pre", null, false);
		addTag("code", attr.isEmpty() ? node.attr() : attr, false);
		sb.append(esc(cNode.literal()));
		addTag("/code", null, false);
		addTag("/pre", null, false);
		cr();
	}

	private void thematicBreak(Node node) {
		cr();
		addTag("hr", attr.isEmpty() ? node.attr() : attr, true);
		cr();
	}

	private void openBlockQuote(Node node) {
		cr();
		addTag("blockquote", attr.isEmpty() ? node.attr() : attr, false);
		cr();
	}
	
	private void closeBlockQuote(Node node) {
		cr();
		addTag("/blockquote", null, false);
		cr();
	}
	
	private void openList(Node node) {
		ListBlockNode lNode = (ListBlockNode) node;
		String tagname = lNode.isOrdered() ? "ol" : "ul";
		if (lNode.isOrdered() && lNode.start() != 1)
			attr.put("start", String.valueOf(lNode.start()));
		if (!attr.isEmpty()) attr.putAll(lNode.attr());
		cr();
		addTag(tagname, attr.isEmpty() ? node.attr() : attr, false);
		cr();
	}
	
	private void closeList(Node node) {
		String tagname = ((ListBlockNode) node).isOrdered() ? "/ol" : "/ul";
		cr();
		addTag(tagname, null, false);
		cr();
	}
	
	private void openItem(Node node) {
		addTag("li", attr.isEmpty() ? node.attr() : attr, false);
	}
	
	private void closeItem(Node node) {
		addTag("/li", null, false);
		cr();
	}
	
	private void htmlBlock(Node node) {
		cr();
		if (safe)
			sb.append("<!-- raw HTML omitted -->");
		else
			sb.append(((HtmlBlockNode) node).literal());
		lastOut = ">";
		cr();
	}
}

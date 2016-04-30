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

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import org.junit.Test;

import nikoladasm.commonmark.nodes.*;

public class HtmlRendererUnitTest {

	private static HtmlRenderer renderer = new HtmlRenderer();
	
	@Test
	public void shouldBeCorrectInlineText() {
		InlineTextNode n = new InlineTextNode();
		n.literal("text");
		String out = "text";
		assertThat(renderer.render(n), is(equalTo(out)));
	}

	@Test
	public void shouldBeCorrectSoftBreak() {
		InlineSoftBreakNode n = new InlineSoftBreakNode();
		String out = "\n";
		assertThat(renderer.render(n), is(equalTo(out)));
	}

	@Test
	public void shouldBeCorrectLineBreak() {
		InlineLineBreakNode n = new InlineLineBreakNode();
		String out = "<br />\n";
		assertThat(renderer.render(n), is(equalTo(out)));
	}

	@Test
	public void shouldBeCorrectLink() {
		InlineLinkNode n = new InlineLinkNode();
		n.reference("http://www.domain.com");
		n.title("title");
		InlineTextNode t = new InlineTextNode();
		t.literal("link");
		n.childs().addLast(t);
		String out = "<a href=\"http://www.domain.com\" title=\"title\">link</a>";
		assertThat(renderer.render(n), is(equalTo(out)));
	}

	@Test
	public void shouldBeCorrectImage() {
		InlineImageNode n = new InlineImageNode();
		n.reference("http://www.domain.com/1.jpg");
		n.title("title");
		InlineTextNode t = new InlineTextNode();
		t.literal("image");
		n.childs().addLast(t);
		String out = "<img src=\"http://www.domain.com/1.jpg\" alt=\"image\" title=\"title\" />";
		assertThat(renderer.render(n), is(equalTo(out)));
	}

	@Test
	public void shouldBeCorrectEmphasis() {
		InlineEmphasisNode n = new InlineEmphasisNode();
		InlineTextNode t = new InlineTextNode();
		t.literal("text");
		n.childs().addLast(t);
		String out = "<em>text</em>";
		assertThat(renderer.render(n), is(equalTo(out)));
	}

	@Test
	public void shouldBeCorrectStrongEmphasis() {
		InlineStrongEmphasisNode n = new InlineStrongEmphasisNode();
		InlineTextNode t = new InlineTextNode();
		t.literal("text");
		n.childs().addLast(t);
		String out = "<strong>text</strong>";
		assertThat(renderer.render(n), is(equalTo(out)));
	}

	@Test
	public void shouldBeCorrectInlineHtml() {
		InlineHtmlNode n = new InlineHtmlNode();
		n.literal("<!--@ -->");
		String out = "<!--@ -->";
		assertThat(renderer.render(n), is(equalTo(out)));
	}

	@Test
	public void shouldBeCorrectInlineCode() {
		InlineCodeNode n = new InlineCodeNode();
		n.literal("ggg``hhh");
		String out = "<code>ggg``hhh</code>";
		assertThat(renderer.render(n), is(equalTo(out)));
	}
	
	@Test
	public void shouldBeCorrectParagraph() {
		DocumentNode node = new DocumentNode(0,0);
		ParagraphBlockNode n = new ParagraphBlockNode(0, 0);
		n.parent(node);
		InlineTextNode t = new InlineTextNode();
		t.literal("text");
		n.childs().addLast(t);
		String out = "<p>text</p>\n";
		assertThat(renderer.render(n), is(equalTo(out)));
	}

	@Test
	public void shouldBeCorrectHeading() {
		HeadingBlockNode n = new HeadingBlockNode(0, 0);
		n.level(3);
		InlineTextNode t = new InlineTextNode();
		t.literal("text");
		n.childs().addLast(t);
		String out = "<h3>text</h3>\n";
		assertThat(renderer.render(n), is(equalTo(out)));
	}

	@Test
	public void shouldBeCorrectCodeBlock() {
		CodeBlockNode n = new CodeBlockNode(0, 0);
		n.literal("ggg hhh");
		n.info("cpp las");
		String out = "<pre><code class=\"language-cpp\">ggg hhh</code></pre>\n";
		assertThat(renderer.render(n), is(equalTo(out)));
	}
	
	@Test
	public void shouldBeCorrectThematicBreak() {
		ThematicBreakNode n = new ThematicBreakNode(0, 0);
		String out = "<hr />\n";
		assertThat(renderer.render(n), is(equalTo(out)));
	}

	@Test
	public void shouldBeCorrectBlockQuote() {
		BlockQuoteNode n = new BlockQuoteNode(0, 0);
		InlineTextNode t = new InlineTextNode();
		t.literal("text");
		n.childs().addLast(t);
		String out = "<blockquote>\ntext\n</blockquote>\n";
		assertThat(renderer.render(n), is(equalTo(out)));
	}

	@Test
	public void shouldBeCorrectListBlock() {
		ListBlockNode n = new ListBlockNode(0, 0);
		String out = "<ul>\n</ul>\n";
		assertThat(renderer.render(n), is(equalTo(out)));
	}

	@Test
	public void shouldBeCorrectItemBlock() {
		ItemBlockNode n = new ItemBlockNode(0, 0);
		InlineTextNode t = new InlineTextNode();
		t.literal("text");
		n.childs().addLast(t);
		String out = "<li>text</li>\n";
		assertThat(renderer.render(n), is(equalTo(out)));
	}

	@Test
	public void shouldBeCorrectHtmlBlock() {
		HtmlBlockNode n = new HtmlBlockNode(0, 0);
		n.literal("<!--@ -->");
		String out = "<!--@ -->\n";
		assertThat(renderer.render(n), is(equalTo(out)));
	}
}

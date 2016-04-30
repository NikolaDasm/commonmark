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

package nikoladasm.commonmark.inlineparser;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import org.junit.Test;

import nikoladasm.commonmark.CharReaderImpl;
import nikoladasm.commonmark.InlineParser;
import nikoladasm.commonmark.InlineParserImpl;
import nikoladasm.commonmark.nodes.BaseBlockNode;
import nikoladasm.commonmark.nodes.BlockNode;
import nikoladasm.commonmark.nodes.InlineHtmlNode;
import nikoladasm.commonmark.nodes.InlineTextNode;
import nikoladasm.commonmark.nodes.Node;

public class ProcessingInstructionUnitTest {

	private static InlineParser ip = new InlineParserImpl(new CharReaderImpl());
	private BlockNode dummyBlockNode = new BaseBlockNode(0, 0) {};

	@Test
	public void shoudBeProcessingInstructionTag() {
		dummyBlockNode.stringContent("<?php echo $a; ?>");
		ip.parseInline(dummyBlockNode);
		assertThat(dummyBlockNode.childs().isEmpty(), is(equalTo(false)));
		Node first = dummyBlockNode.childs().removeFirst();
		assertThat(first, instanceOf(InlineHtmlNode.class));
		assertThat(((InlineHtmlNode) first).literal(), is(equalTo("<?php echo $a; ?>")));
		dummyBlockNode.childs().clear();
		dummyBlockNode.stringContent("<?php@ echo $a; ?>");
		ip.parseInline(dummyBlockNode);
		assertThat(dummyBlockNode.childs().isEmpty(), is(equalTo(false)));
		first = dummyBlockNode.childs().removeFirst();
		assertThat(first, instanceOf(InlineHtmlNode.class));
		assertThat(((InlineHtmlNode) first).literal(), is(equalTo("<?php@ echo $a; ?>")));
		dummyBlockNode.stringContent("<?>>> mWaitingRequests =\n		new HashMap<String, Queue<Request<?>");
		ip.parseInline(dummyBlockNode);
		assertThat(dummyBlockNode.childs().isEmpty(), is(equalTo(false)));
		first = dummyBlockNode.childs().removeFirst();
		assertThat(first, instanceOf(InlineHtmlNode.class));
		assertThat(((InlineHtmlNode) first).literal(), is(equalTo("<?>>> mWaitingRequests =\n		new HashMap<String, Queue<Request<?>")));
	}
	
	@Test
	public void shouldBeTextNodeWhenProcessingInstructionTagIsIncorrect() {
		dummyBlockNode.stringContent("<?>");
		ip.parseInline(dummyBlockNode);
		assertThat(dummyBlockNode.childs().isEmpty(), is(equalTo(false)));
		Node first = dummyBlockNode.childs().removeFirst();
		assertThat(first, instanceOf(InlineTextNode.class));
		assertThat(((InlineTextNode) first).literal(), is(equalTo("<?>")));
	}

}

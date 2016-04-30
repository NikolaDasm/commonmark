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

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import nikoladasm.commonmark.CharReaderImpl;
import nikoladasm.commonmark.InlineParser;
import nikoladasm.commonmark.InlineParserImpl;
import nikoladasm.commonmark.nodes.*;

@RunWith(Parameterized.class)
public class InlineImageUnitTest {

	private static InlineParser ip = new InlineParserImpl(new CharReaderImpl());
	private BlockNode dummyBlockNode = new BaseBlockNode(0, 0) {};

	@Parameters
	public static Collection<Object[]> data() throws Exception {
		return Arrays.asList(new Object[][] {
			{"![foo](/url \"title\")",
				"/url",
				"title",
				"foo"},
			{"![foo](train.jpg)",
				"train.jpg",
				"",
				"foo"},
			{"![foo](<url>)",
				"url",
				"",
				"foo"},
		});
	}

	@Parameter(0)
	public String input;
	
	@Parameter(1)
	public String reference;
	
	@Parameter(2)
	public String title;
	
	@Parameter(3)
	public String textLiteral;
	
	@Test
	public void test() {
		dummyBlockNode.stringContent(input);
		ip.parseInline(dummyBlockNode);
		assertThat(dummyBlockNode.childs().isEmpty(), is(equalTo(false)));
		Node first = dummyBlockNode.childs().removeFirst();
		assertThat(first, instanceOf(InlineImageNode.class));
		assertThat(((InlineImageNode) first).reference(), is(equalTo(reference)));
		assertThat(((InlineImageNode) first).title(), is(equalTo(title)));
		assertThat(first.childs().isEmpty(), is(equalTo(false)));
		first = first.childs().removeFirst();
		assertThat(first, instanceOf(InlineTextNode.class));
		assertThat(((InlineTextNode) first).literal(), is(equalTo(textLiteral)));
	}
}

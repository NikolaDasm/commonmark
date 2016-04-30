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

package nikoladasm.commonmark.blockparser;

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
import nikoladasm.commonmark.InlineParserImpl;
import nikoladasm.commonmark.Parser;
import nikoladasm.commonmark.nodes.InlineTextNode;
import nikoladasm.commonmark.nodes.Node;
import nikoladasm.commonmark.nodes.ParagraphBlockNode;

@RunWith(Parameterized.class)
public class IncorrectLinkReferenceDefinitionsUnitTest {

	private static Parser p = new Parser(new InlineParserImpl(new CharReaderImpl()));

	@Parameters
	public static Collection<Object[]> data() throws Exception {
		return Arrays.asList(new Object[][] {
			{"[foo]: /url 'title\n\nwith blank line'", "[foo]: /url 'title"},
			{"[foo]:", "[foo]:"},
			{"[foo]: /url \"title\" ok", "[foo]: /url \"title\" ok"},
			{"Foo\n[bar]: /baz", "Foo"},
		});
	}

	@Parameter(0)
	public String input;
	
	@Parameter(1)
	public String output;
	
	@Test
	public void test() {
		Node node = p.parse(input);
		assertThat(node.childs().isEmpty(), is(equalTo(false)));
		Node first = node.childs().removeFirst();
		assertThat(first, instanceOf(ParagraphBlockNode.class));
		assertThat(first.childs().isEmpty(), is(equalTo(false)));
		first = first.childs().removeFirst();
		assertThat(first, instanceOf(InlineTextNode.class));
		assertThat(((InlineTextNode) first).literal(), is(equalTo(output)));
	}
}

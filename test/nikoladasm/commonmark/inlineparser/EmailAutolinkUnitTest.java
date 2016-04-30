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
public class EmailAutolinkUnitTest {
	
	private static InlineParser ip = new InlineParserImpl(new CharReaderImpl());
	private BlockNode dummyBlockNode = new BaseBlockNode(0, 0) {};

	@Parameters
	public static Collection<Object[]> data() throws Exception {
		return Arrays.asList(new Object[][] {
			{"<0foo@bar.example.com>",
				"mailto:0foo@bar.example.com",
				"",
				"0foo@bar.example.com"},
			{"<+foo+special@Bar.baz-bar0.com>",
				"mailto:+foo+special@Bar.baz-bar0.com",
				"",
				"+foo+special@Bar.baz-bar0.com"},
			{"</foo+special@Bar.baz-bar0.com>",
				"mailto:/foo+special@Bar.baz-bar0.com",
				"",
				"/foo+special@Bar.baz-bar0.com"},
			{"</foo-special@Bar.baz-bar0.com>",
				"mailto:/foo-special@Bar.baz-bar0.com",
				"",
				"/foo-special@Bar.baz-bar0.com"},
			{"<!.foo-special@Bar.baz-bar0.com>",
				"mailto:!.foo-special@Bar.baz-bar0.com",
				"",
				"!.foo-special@Bar.baz-bar0.com"},
			{"<!@Bar.baz-bar0.com>",
				"mailto:!@Bar.baz-bar0.com",
				"",
				"!@Bar.baz-bar0.com"},
			{"<!-foo@Bar.baz-bar0.com>",
				"mailto:!-foo@Bar.baz-bar0.com",
				"",
				"!-foo@Bar.baz-bar0.com"},
			{"<!Foo@Bar.baz-bar0.com>",
				"mailto:!Foo@Bar.baz-bar0.com",
				"",
				"!Foo@Bar.baz-bar0.com"},
			{"<!FOO@Bar.baz-bar0.com>",
				"mailto:!FOO@Bar.baz-bar0.com",
				"",
				"!FOO@Bar.baz-bar0.com"},
			{"<!--FOO@Bar.baz-bar0.com>",
				"mailto:!--FOO@Bar.baz-bar0.com",
				"",
				"!--FOO@Bar.baz-bar0.com"},
			{"<?FOO@Bar.baz-bar0.com>",
				"mailto:?FOO@Bar.baz-bar0.com",
				"",
				"?FOO@Bar.baz-bar0.com"},
			{"<FOO@Bar.baz-bar0.com>",
				"mailto:FOO@Bar.baz-bar0.com",
				"",
				"FOO@Bar.baz-bar0.com"},
			{"<FO#O@Bar.baz-bar0.com>",
				"mailto:FO#O@Bar.baz-bar0.com",
				"",
				"FO#O@Bar.baz-bar0.com"},
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
		assertThat(first, instanceOf(InlineLinkNode.class));
		assertThat(((InlineLinkNode) first).reference(), is(equalTo(reference)));
		assertThat(((InlineLinkNode) first).title(), is(equalTo(title)));
		assertThat(first.childs().isEmpty(), is(equalTo(false)));
		first = first.childs().removeFirst();
		assertThat(first, instanceOf(InlineTextNode.class));
		assertThat(((InlineTextNode) first).literal(), is(equalTo(textLiteral)));
	}
}

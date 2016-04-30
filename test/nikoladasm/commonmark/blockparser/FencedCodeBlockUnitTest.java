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
import nikoladasm.commonmark.nodes.*;

@RunWith(Parameterized.class)
public class FencedCodeBlockUnitTest {

	private static Parser p = new Parser(new InlineParserImpl(new CharReaderImpl()));

	@Parameters
	public static Collection<Object[]> data() throws Exception {
		return Arrays.asList(new Object[][] {
			{"```\n<\n >\n```", "<\n >\n", ""},
			{"~~~\n<\n >\n~~~", "<\n >\n", ""},
			{"```\naaa\n~~~\n```", "aaa\n~~~\n", ""},
			{"~~~\naaa\n```\n~~~", "aaa\n```\n", ""},
			{"````\naaa\n```\n``````", "aaa\n```\n", ""},
			{"~~~~\naaa\n~~~\n~~~~", "aaa\n~~~\n", ""},
			{"```", "", ""},
			{"`````\n\n```\naaa", "\n```\naaa\n", ""},
			{"```\n\n  \n```", "\n  \n", ""},
			{"```\n```", "", ""},
			{" ```\n aaa\naaa\n```", "aaa\naaa\n", ""},
			{"  ```\naaa\n  aaa\naaa\n  ```", "aaa\naaa\naaa\n", ""},
			{"   ```\n   aaa\n    aaa\n  aaa\n   ```", "aaa\n aaa\naaa\n", ""},
			{"```\naaa\n  ```", "aaa\n", ""},
			{"   ```\naaa\n  ```", "aaa\n", ""},
			{"```\naaa\n    ```", "aaa\n    ```\n", ""},
			{"~~~~~~\naaa\n~~~ ~~", "aaa\n~~~ ~~\n", ""},
			{"```ruby\ndef foo(x)\n  return 3\nend\n```", "def foo(x)\n  return 3\nend\n", "ruby"},
			{"~~~~    ruby startline=3 $%@#$\ndef foo(x)\n  return 3\nend\n~~~~~~~", "def foo(x)\n  return 3\nend\n", "ruby startline=3 $%@#$"},
			{"````;\n````", "", ";"},
			{"```\n``` aaa\n```", "``` aaa\n", ""},
		});
	}

	@Parameter(0)
	public String input;
	
	@Parameter(1)
	public String output;
	
	@Parameter(2)
	public String info;
	
	@Test
	public void test() {
		Node node = p.parse(input);
		assertThat(node.childs().isEmpty(), is(equalTo(false)));
		Node first = node.childs().removeFirst();
		assertThat(first, instanceOf(CodeBlockNode.class));
		assertThat(((CodeBlockNode) first).isFenced(), is(equalTo(true)));
		assertThat(((CodeBlockNode) first).info(), is(equalTo(info)));
		assertThat(((CodeBlockNode) first).literal(), is(equalTo(output)));
	}
}

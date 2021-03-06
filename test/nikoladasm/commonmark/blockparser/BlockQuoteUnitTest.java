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
public class BlockQuoteUnitTest {

	private static Parser p = new Parser(new InlineParserImpl(new CharReaderImpl()));

	@Parameters
	public static Collection<Object[]> data() throws Exception {
		return Arrays.asList(new Object[][] {
			{"> # Foo\n> bar\n> baz"},
			{"># Foo\n>bar\n> baz"},
			{"   > # Foo\n   > bar\n > baz"},
			{"> # Foo\n> bar\nbaz"},
			{"> bar\nbaz\n> foo"},
			{"> foo\n    - bar"},
			{">"},
			{">\n>  \n> "},
			{">\n> foo\n>  "},
			{"> foo\n> bar"},
			{"> foo\n>\n> bar"},
			{"> bar\nbaz"},
		});
	}

	@Parameter(0)
	public String input;
	
	@Test
	public void test() {
		Node node = p.parse(input);
		assertThat(node.childs().size(), is(equalTo(1)));
		Node first = node.childs().removeFirst();
		assertThat(first, instanceOf(BlockQuoteNode.class));
	}
}

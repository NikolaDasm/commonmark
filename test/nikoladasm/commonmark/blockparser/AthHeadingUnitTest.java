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
public class AthHeadingUnitTest {

	private static Parser p = new Parser(new InlineParserImpl(new CharReaderImpl()));

	@Parameters(name = "{index}: {0}")
	public static Collection<Object[]> data() throws Exception {
		return Arrays.asList(new Object[][] {
			{"# foo", 1, "foo"},
			{"## foo", 2, "foo"},
			{"### foo", 3, "foo"},
			{"#### foo", 4, "foo"},
			{"##### foo", 5, "foo"},
			{"###### foo", 6, "foo"},
			{"# foo *bar* \\*baz\\*", 1, "foo "},
			{"#                  foo                     ", 1, "foo"},
			{" ### foo", 3, "foo"},
			{"  ## foo", 2, "foo"},
			{"   # foo", 1, "foo"},
			{"## foo ##", 2, "foo"},
			{"  ###   bar    ###", 3, "bar"},
			{"# foo ##################################", 1, "foo"},
			{"##### foo ##", 5, "foo"},
			{"### foo ###     ", 3, "foo"},
			{"### foo ### b", 3, "foo ### b"},
			{"# foo#", 1, "foo#"},
			{"### foo \\###", 3, "foo ###"},
			{"## foo #\\##", 2, "foo ###"},
			{"# foo \\#", 1, "foo #"},
		});
	}

	@Parameter(0)
	public String input;
	
	@Parameter(1)
	public int level;
	
	@Parameter(2)
	public String output;
	
	@Test
	public void test() {
		Node node = p.parse(input);
		assertThat(node.childs().isEmpty(), is(equalTo(false)));
		Node first = node.childs().removeFirst();
		assertThat(first, instanceOf(HeadingBlockNode.class));
		assertThat(((HeadingBlockNode) first).level(), is(equalTo(level)));
		assertThat(first.childs().isEmpty(), is(equalTo(false)));
		first = first.childs().removeFirst();
		assertThat(first, instanceOf(InlineTextNode.class));
		assertThat(((InlineTextNode) first).literal(), is(equalTo(output)));
	}
}

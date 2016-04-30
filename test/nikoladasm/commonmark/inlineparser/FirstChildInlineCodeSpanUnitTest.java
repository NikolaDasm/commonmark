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
public class FirstChildInlineCodeSpanUnitTest {

	private static InlineParser ip = new InlineParserImpl(new CharReaderImpl());
	private BlockNode dummyBlockNode = new BaseBlockNode(0, 0) {};

	@Parameters
	public static Collection<Object[]> data() throws Exception {
		return Arrays.asList(new Object[][] {
			{"`foo`",					"foo"},
			{"`` foo ` bar  ``",		"foo ` bar"},
			{"` `` `",					"``"},
			{"``\nfoo\n``",				"foo"},
			{"`foo   bar\n  baz`",		"foo bar baz"},
			{"`foo `` bar`",			"foo `` bar"},
			{"`foo\\`bar`",				"foo\\"},
			{"`<a href=\"`\">`",		"<a href=\""},
			{"`<http://foo.bar.`baz>`",	"<http://foo.bar."},
		});
	}

	@Parameter(0)
	public String input;
	
	@Parameter(1)
	public String codeSpanLiteral;
	
	@Test
	public void test() {
		dummyBlockNode.stringContent(input);
		ip.parseInline(dummyBlockNode);
		assertThat(dummyBlockNode.childs().isEmpty(), is(equalTo(false)));
		Node first = dummyBlockNode.childs().getFirst();
		assertThat(first, instanceOf(InlineCodeNode.class));
		assertThat(((InlineCodeNode) first).literal(), is(equalTo(codeSpanLiteral)));
	}
}

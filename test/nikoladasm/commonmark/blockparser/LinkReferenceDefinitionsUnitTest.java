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

import static org.hamcrest.CoreMatchers.*;
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
import nikoladasm.commonmark.Link;
import nikoladasm.commonmark.Parser;

@RunWith(Parameterized.class)
public class LinkReferenceDefinitionsUnitTest {

	private InlineParser ip = new InlineParserImpl(new CharReaderImpl());
	private Parser p = new Parser(ip);

	@Parameters
	public static Collection<Object[]> data() throws Exception {
		return Arrays.asList(new Object[][] {
			{"[foo]: /url \"title\"",
				"/url",
				"title",
				"FOO"},
			{"   [foo]: \n      /url  \n           'the title'  \n",
				"/url",
				"the title",
				"FOO"},
			{"[Foo*bar\\]]:my_(url) 'title (with parens)'",
				"my_(url)",
				"title (with parens)",
				"FOO*BAR\\]"},
			{"[Foo bar]:\n<my%20url>\n'title'",
				"my%20url",
				"title",
				"FOO BAR"},
			{"[foo]: /url '\ntitle\nline1\nline2\n'",
				"/url",
				"\ntitle\nline1\nline2\n",
				"FOO"},
			{"[foo]:\n/url",
				"/url",
				"",
				"FOO"},
			{"[foo]: /url\\bar\\*baz \"foo\\\"bar\baz\"",
				"/url%5Cbar*baz",
				"foo\"bar\baz",
				"FOO"},
			{"[foo]: first\n[foo]: second",
				"first",
				"",
				"FOO"},
			{"[ΑΓω]: /φου",
				"/%CF%86%CE%BF%CF%85",
				"",
				"ΑΓΩ"},
			{"[\nfoo\n]: /url",
				"/url",
				"",
				"FOO"},
			{"[foo]: /url\n\"title\" ok",
				"/url",
				"",
				"FOO"},
		});
	}

	@Parameter(0)
	public String input;
	
	@Parameter(1)
	public String reference;
	
	@Parameter(2)
	public String title;
	
	@Parameter(3)
	public String label;
	
	@Test
	public void test() {
		p.parse(input);
		Link link = ip.refmap().get(label);
		assertThat(link, is(notNullValue()));
		assertThat(link.reference, is(equalTo(reference)));
		assertThat(link.title, is(equalTo(title)));
	}
}

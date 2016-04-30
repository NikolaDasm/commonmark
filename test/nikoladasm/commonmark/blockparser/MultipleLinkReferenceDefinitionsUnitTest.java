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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

import org.junit.Test;

import nikoladasm.commonmark.CharReaderImpl;
import nikoladasm.commonmark.InlineParser;
import nikoladasm.commonmark.InlineParserImpl;
import nikoladasm.commonmark.Link;
import nikoladasm.commonmark.Parser;

public class MultipleLinkReferenceDefinitionsUnitTest {

	private InlineParser ip = new InlineParserImpl(new CharReaderImpl());
	private Parser p = new Parser(ip);

	@Test
	public void shouldBeCorrectLinkReferenceDefinition() {
		p.parse("[foo]: /foo-url \"foo\"\n  [bar]: /bar-url\n  \"bar\"\n[baz]: /baz-url");
		Link link = ip.refmap().get("FOO");
		assertThat(link, is(notNullValue()));
		assertThat(link.reference, is(equalTo("/foo-url")));
		assertThat(link.title, is(equalTo("foo")));
		link = ip.refmap().get("BAR");
		assertThat(link, is(notNullValue()));
		assertThat(link.reference, is(equalTo("/bar-url")));
		assertThat(link.title, is(equalTo("bar")));
		link = ip.refmap().get("BAZ");
		assertThat(link, is(notNullValue()));
		assertThat(link.reference, is(equalTo("/baz-url")));
		assertThat(link.title, is(equalTo("")));
	}

}

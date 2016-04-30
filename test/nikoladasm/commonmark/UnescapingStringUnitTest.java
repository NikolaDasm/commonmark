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

package nikoladasm.commonmark;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import org.junit.Test;

public class UnescapingStringUnitTest {
	
	private static InlineParser ip = new InlineParserImpl(new CharReaderImpl());

	@Test
	public void shouldBeUnescapingString() {
		String str = "\\!\\\"\\#\\$\\%\\&\\'\\(\\)\\*\\+\\,";
		str += "\\-\\.\\/\\:\\;\\<\\=\\>\\?\\@\\[\\\\\\]\\^\\_\\`\\{\\|\\}\\~";
		String uStr = ip.unescapeString(str);
		String expected = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";
		assertThat(uStr, is(equalTo(expected)));
	}

	@Test
	public void shouldBeUnchangingString() {
		String str = "\\\t\\A\\a\\ \\3\\φ\\«";
		String uStr = ip.unescapeString(str);
		String expected = "\\\t\\A\\a\\ \\3\\φ\\«";
		assertThat(uStr, is(equalTo(expected)));
	}

	@Test
	public void shouldBeDecodedEntityString() {
		String str = "&amp;&gt;&lt;";
		String uStr = ip.unescapeString(str);
		String expected = "&><";
		assertThat(uStr, is(equalTo(expected)));
	}
}

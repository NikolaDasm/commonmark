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
public class HtmlBlockUnitTest {

	private static Parser p = new Parser(new InlineParserImpl(new CharReaderImpl()));

	@Parameters
	public static Collection<Object[]> data() throws Exception {
		return Arrays.asList(new Object[][] {
			{"<table>\n  <tr>\n    <td>\n           hi\n    </td>\n  </tr>\n</table>\n\n",
				"<table>\n  <tr>\n    <td>\n           hi\n    </td>\n  </tr>\n</table>"},
			{" <div>\n  *hello*\n         <foo><a>", " <div>\n  *hello*\n         <foo><a>"},
			{"</div>\n*foo*", "</div>\n*foo*"},
			{"<DIV CLASS=\"foo\">", "<DIV CLASS=\"foo\">"},
			{"<div id=\"foo\"\n  class=\"bar\">\n</div>", "<div id=\"foo\"\n  class=\"bar\">\n</div>"},
			{"<div id=\"foo\" class=\"bar\n  baz\">\n</div>", "<div id=\"foo\" class=\"bar\n  baz\">\n</div>"},
			{"<div>\n*foo*\n\n*bar*", "<div>\n*foo*"},
			{"<div id=\"foo\"\n*hi*", "<div id=\"foo\"\n*hi*"},
			{"<div class\nfoo", "<div class\nfoo"},
			{"<div *???-&&&-<---\n*foo*", "<div *???-&&&-<---\n*foo*"},
			{"<div><a href=\"bar\">*foo*</a></div>", "<div><a href=\"bar\">*foo*</a></div>"},
			{"<table><tr><td>\nfoo\n</td></tr></table>", "<table><tr><td>\nfoo\n</td></tr></table>"},
			{"<div></div>\n``` c\nint x = 33;\n```", "<div></div>\n``` c\nint x = 33;\n```"},
			{"<a href=\"foo\">\n*bar*\n</a>", "<a href=\"foo\">\n*bar*\n</a>"},
			{"<Warning>\n*bar*\n</Warning>", "<Warning>\n*bar*\n</Warning>"},
			{"<i class=\"foo\">\n*bar*\n</i>", "<i class=\"foo\">\n*bar*\n</i>"},
			{"</ins>\n*bar*", "</ins>\n*bar*"},
			{"<del>\n*foo*\n</del>", "<del>\n*foo*\n</del>"},
			{"<pre language=\"haskell\"><code>\nimport Text.HTML.TagSoup\n\nmain :: IO ()\nmain = print $ parseTags tags\n</code></pre>",
				"<pre language=\"haskell\"><code>\nimport Text.HTML.TagSoup\n\nmain :: IO ()\nmain = print $ parseTags tags\n</code></pre>"},
			{"<script type=\"text/javascript\">\n// JavaScript example\n\ndocument.getElementById(\"demo\").innerHTML = \"Hello JavaScript!\";\n</script>",
				"<script type=\"text/javascript\">\n// JavaScript example\n\ndocument.getElementById(\"demo\").innerHTML = \"Hello JavaScript!\";\n</script>"},
			{"<style\n  type=\"text/css\">\nh1 {color:red;}\n\np {color:blue;}\n</style>",
				"<style\n  type=\"text/css\">\nh1 {color:red;}\n\np {color:blue;}\n</style>"},
			{"<style\n  type=\"text/css\">\n\nfoo", "<style\n  type=\"text/css\">\n\nfoo"},
			{"<style>p{color:red;}</style>\n*foo*", "<style>p{color:red;}</style>"},
			{"<!-- foo -->*bar*\n*baz*", "<!-- foo -->*bar*"},
			{"<script>\nfoo\n</script>1. *bar*", "<script>\nfoo\n</script>1. *bar*"},
			{"<!-- Foo\n\nbar\n   baz -->", "<!-- Foo\n\nbar\n   baz -->"},
			{"<?php\n\n  echo '>';\n\n?>", "<?php\n\n  echo '>';\n\n?>"},
			{"<!DOCTYPE html>", "<!DOCTYPE html>"},
			{"<![CDATA[\nfunction matchwo(a,b)\n{\n  if (a < b && a < 0) then {\n    return 1;\n\n  } else {\n\n    return 0;\n  }\n}\n]]>",
				"<![CDATA[\nfunction matchwo(a,b)\n{\n  if (a < b && a < 0) then {\n    return 1;\n\n  } else {\n\n    return 0;\n  }\n}\n]]>"},
			{"<br/> ", "<br/> "},
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
		assertThat(first, instanceOf(HtmlBlockNode.class));
		assertThat(((HtmlBlockNode) first).literal(), is(equalTo(output)));
	}
}

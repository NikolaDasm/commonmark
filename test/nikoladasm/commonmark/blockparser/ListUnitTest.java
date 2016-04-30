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
public class ListUnitTest {

	private static Parser p = new Parser(new InlineParserImpl(new CharReaderImpl()));

	@Parameters
	public static Collection<Object[]> data() throws Exception {
		return Arrays.asList(new Object[][] {
			{"1.  A paragraph\n    with two lines.\n\n        indented code\n\n    > A block quote.",
				true, 1},
			{"- one\n\n two", false, 1},
			{"- one\n\n  two", false, 1},
			{" -    one\n\n     two", false, 1},
			{" -    one\n\n      two", false, 1},
			{"- foo\n\n  bar\n\n- foo\n\n\n  bar\n\n- ```\n  foo\n\n\n  bar\n  ```\n\n- baz\n\n  + ```\n    foo\n\n\n    bar\n    ```",
				false, 2},
			{"1.  foo\n\n    ```\n    bar\n    ```\n\n    baz\n\n    > bam",
				true, 1},
			{"- Foo\n\n      bar\n\n      baz", false, 1},
			{"- Foo\n\n      bar\n\n\n      baz", false, 1},
			{"123456789. ok", true, 1},
			{"0. ok", true, 1},
			{"003. ok", true, 1},
			{"- foo\n\n      bar", false, 1},
			{"  10.  foo\n\n           bar", true, 1},
			{"1.     indented code\n\n   paragraph\n\n       more code", true, 1},
			{"1.      indented code\n\n   paragraph\n\n       more code", true, 1},
			{"-    foo\n\n  bar", false, 1},
			{"-  foo\n\n   bar", false, 1},
			{"-\n  foo\n-\n  ```\n  bar\n  ```\n-\n      baz", false, 3},
			{"-\n\n				  foo", false, 1},
			{"- foo\n-\n- bar", false, 3},
			{"- foo\n-   \n- bar", false, 3},
			{"1. foo\n2.\n3. bar", true, 3},
			{"*", false, 1},
			{" 1.  A paragraph\n     with two lines.\n\n         indented code\n\n     > A block quote.",
				true, 1},
			{"  1.  A paragraph\n      with two lines.\n\n          indented code\n\n      > A block quote.",
				true, 1},
			{"   1.  A paragraph\n       with two lines.\n\n           indented code\n\n       > A block quote.",
				true, 1},
			{"  1.  A paragraph\nwith two lines.\n\n          indented code\n\n      > A block quote.",
				true, 1},
			{"  1.  A paragraph\n    with two lines.", true, 1},
			{"- foo\n  - bar\n    - baz", false, 1},
			{"- foo\n - bar\n  - baz", false, 3},
			{"10) foo\n    - bar", true, 1},
			{"10) foo\n   - bar", true, 1},
			{"- - foo", false, 1},
			{"1. - 2. foo", true, 1},
			{"- # Foo\n- Bar\n  ---\n  baz", false, 2},
			{"- foo\n- bar\n+ baz", false, 2},
			{"1. foo\n2. bar\n3) baz", true, 2},
			{"- foo\n\n- bar\n\n\n- baz", false, 2},
			{"- foo\n\n\n  bar\n- baz", false, 1},
			{"- foo\n- bar\n\n\n- baz\n- bim", false, 2},
			{"-   foo\n\n    notcode\n\n-   foo\n\n\n    code", false, 2},
			{"- a\n - b\n  - c\n   - d\n    - e\n   - f\n  - g\n - h\n- i", false, 9},
			{"1. a\n\n  2. b\n\n    3. c", true, 3},
			{"- a\n- b\n\n- c", false, 3},
			{"* a\n*\n\n* c", false, 3},
			{"- a\n- b\n\n  c\n- d", false, 3},
			{"- a\n- b\n\n  [ref]: /url\n- d", false, 3},
			{"- a\n- ```\n  b\n\n\n  ```\n- c", false, 3},
			{"* a\n  > b\n  >\n* c", false, 2},
			{"- a\n  > b\n  ```\n  c\n  ```\n- d", false, 2},
			{"- a", false, 1},
			{"1. ```\n   foo\n   ```\n\n   bar", true, 1},
		});
	}

	@Parameter(0)
	public String input;
	
	@Parameter(1)
	public boolean ordered;
	
	@Parameter(2)
	public int itemCount;
	
	@Test
	public void test() {
		Node node = p.parse(input);
		assertThat(node.childs().isEmpty(), is(equalTo(false)));
		Node first = node.childs().removeFirst();
		assertThat(first, instanceOf(ListBlockNode.class));
		assertThat(((ListBlockNode) first).isOrdered(), is(equalTo(ordered)));
		assertThat(first.childs().isEmpty(), is(equalTo(false)));
		assertThat(first.childs().size(), is(equalTo(itemCount)));
		first = first.childs().removeFirst();
		assertThat(first, instanceOf(ItemBlockNode.class));
	}
}

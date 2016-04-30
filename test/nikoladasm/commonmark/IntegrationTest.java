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

import static nikoladasm.commonmark.TestDataUtil.readTestData;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import nikoladasm.commonmark.TestDataUtil.TestDataContainer;
import nikoladasm.commonmark.nodes.Node;

@RunWith(Parameterized.class)
public class IntegrationTest {

	private static InlineParser ip = new InlineParserImpl(new CharReaderImpl());
	private static Parser p = new Parser(ip);
	private static HtmlRenderer r = new HtmlRenderer();

	@Parameters(name = "{index}: {2}")
	public static Collection<Object[]> data() throws Exception {
		List<TestDataContainer> inTestData =
			readTestData(Paths.get("testdata/input.text"), Paths.get("testdata/output.text"));
		List<Object[]> testData = new LinkedList<>();
		inTestData.forEach(data -> {
			testData.add(new String[]{data.input, data.output, data.name});
		});
		return testData;
	}

	@Parameter(0)
	public String input;
	
	@Parameter(1)
	public String output;
	
	@Parameter(2)
	public String name;

	@Before
	public void clearMap() {
		ip.refmap().clear();
	}
	
	@Test
	public void test() {
		Node node = p.parse(input);
		String output = r.render(node);
		String expected = this.output+'\n';
		assertThat(output, is(equalTo(expected)));
	}
}

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

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import nikoladasm.commonmark.TestDataUtil.TestDataContainer;
import nikoladasm.commonmark.nodes.*;

@RunWith(Parameterized.class)
public class InlineIntegrationTest {

	private static HtmlRenderer r = new HtmlRenderer();
	private static InlineParser ip = new InlineParserImpl(new CharReaderImpl());
	private BlockNode dummyBlockNode = new BaseBlockNode(0, 0) {};

	@Parameters(name = "{index}: {2}")
	public static Collection<Object[]> data() throws Exception {
		List<TestDataContainer> inTestData =
			readTestData(Paths.get("testdata/inline/input.text"), Paths.get("testdata/inline/output.text"));
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

	@BeforeClass
	public static void initReferenceMap() {
		Link link = new Link();
		link.reference = "/bar*";
		link.title = "ti*tle";
		ip.refmap().put("FOO293", link);
		link = new Link();
		link.reference = "/f%C3%B6%C3%B6";
		link.title = "föö";
		ip.refmap().put("FOO303", link);
		link = new Link();
		link.reference = "/url";
		link.title = "title";
		ip.refmap().put("BAR490", link);
		link = new Link();
		link.reference = "/uri";
		link.title = "";
		ip.refmap().put("REF491", link);
		ip.refmap().put("REF492", link);
		ip.refmap().put("REF493", link);
		ip.refmap().put("REF494", link);
		ip.refmap().put("REF495", link);
		ip.refmap().put("REF496", link);
		ip.refmap().put("REF497", link);
		ip.refmap().put("REF498", link);
		link = new Link();
		link.reference = "/url";
		link.title = "title";
		ip.refmap().put("BAR502", link);
		link = new Link();
		link.reference = "/url";
		link.title = "";
		ip.refmap().put("ТОЛПОЙ503", link);
		link = new Link();
		link.reference = "/url";
		link.title = "title";
		ip.refmap().put("BAR505", link);
		ip.refmap().put("BAR506", link);
		link = new Link();
		link.reference = "/url";
		link.title = "";
		ip.refmap().put("FOO!508", link);
		link = new Link();
		link.reference = "/uri";
		link.title = "";
		ip.refmap().put("BAR\\\\513", link);
		link = new Link();
		link.reference = "/url";
		link.title = "title";
		ip.refmap().put("FOO516", link);
		ip.refmap().put("*FOO* BAR517", link);
		ip.refmap().put("FOO518", link);
		ip.refmap().put("FOO519", link);
		ip.refmap().put("FOO520", link);
		ip.refmap().put("*FOO* BAR521", link);
		ip.refmap().put("*FOO* BAR522", link);
		link = new Link();
		link.reference = "/url";
		link.title = "";
		ip.refmap().put("FOO523", link);
		link = new Link();
		link.reference = "/url";
		link.title = "title";
		ip.refmap().put("FOO524", link);
		link = new Link();
		link.reference = "/url";
		link.title = "";
		ip.refmap().put("FOO525", link);
		ip.refmap().put("FOO526", link);
		ip.refmap().put("FOO*527", link);
		link = new Link();
		link.reference = "/url1";
		link.title = "";
		ip.refmap().put("FOO528", link);
		link = new Link();
		link.reference = "/url2";
		link.title = "";
		ip.refmap().put("BAR528", link);
		link = new Link();
		link.reference = "/url";
		link.title = "";
		ip.refmap().put("BAZ529", link);
		link = new Link();
		link.reference = "/url1";
		link.title = "";
		ip.refmap().put("BAZ530", link);
		link = new Link();
		link.reference = "/url2";
		link.title = "";
		ip.refmap().put("BAR530", link);
		link = new Link();
		link.reference = "/url1";
		link.title = "";
		ip.refmap().put("BAZ531", link);
		link = new Link();
		link.reference = "/url2";
		link.title = "";
		ip.refmap().put("FOO531", link);
		link = new Link();
		link.reference = "train.jpg";
		link.title = "train & tracks";
		ip.refmap().put("FOO *BAR*533", link);
		ip.refmap().put("FOO *BAR*536", link);
		ip.refmap().put("FOOBAR537", link);
		link = new Link();
		link.reference = "/url";
		link.title = "";
		ip.refmap().put("BAR542", link);
		ip.refmap().put("BAR543", link);
		link = new Link();
		link.reference = "/url";
		link.title = "title";
		ip.refmap().put("FOO544", link);
		ip.refmap().put("*FOO* BAR545", link);
		ip.refmap().put("FOO546", link);
		ip.refmap().put("FOO547", link);
		ip.refmap().put("FOO548", link);
		ip.refmap().put("*FOO* BAR549", link);
		ip.refmap().put("FOO551", link);
		ip.refmap().put("FOO552", link);
		ip.refmap().put("FOO553", link);
	}
	
	@Test
	public void test() {
		dummyBlockNode.stringContent(input);
		ip.parseInline(dummyBlockNode);
		String output = r.render(dummyBlockNode);
		assertThat(output, is(equalTo(this.output)));
	}
}

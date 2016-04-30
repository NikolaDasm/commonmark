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

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static org.hamcrest.CoreMatchers.*;

import static nikoladasm.commonmark.UrlUtil.*;

@RunWith(Parameterized.class)
public class UrlUtilUnitTest {

	@Parameters
	public static Collection<Object[]> data() throws Exception {
		return Arrays.asList(new Object[][] {
			{"http://test.example.com/gif.latex?X_t_1%3DX_t_0%5Ctimes%7Be%5E%7B-%5Clambda%5Ctimes%7Bt%7D%7D%7D",
				"http://test.example.com/gif.latex?X_t_1%3DX_t_0%5Ctimes%7Be%5E%7B-%5Clambda%5Ctimes%7Bt%7D%7D%7D"},
			{"http://test.example.com/2012/12/17/%e7%bb%99vmware%e4%b8%8b%e7%9a%84ubuntu-server%e5%85%b1%e4%ba%ab%e6%96%87%e4%bb%b6/",
				"http://test.example.com/2012/12/17/%E7%BB%99vmware%E4%B8%8B%E7%9A%84ubuntu-server%E5%85%B1%E4%BA%AB%E6%96%87%E4%BB%B6/"},
		});
	}

	@Parameter(0)
	public String input;
	
	@Parameter(1)
	public String expected;
	
	@Test
	public void shouldBeCorrectDecodedUrl() {
		assertThat(percentEncode(percentDecode(input)), is(equalTo(expected)));
	}

}

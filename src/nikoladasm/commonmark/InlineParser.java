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

import java.util.Map;
import java.util.function.Function;

import nikoladasm.commonmark.nodes.*;

public interface InlineParser {
	void whitespaseCharFunc(Function<Character,Boolean> func);
	Map<String,Link> refmap();
	void recreateStringBuilders();
	boolean isWhitespase(char c);
	String unescapeString(String str);
	void parseInline(BlockNode root);
	int parseReferences(String input);
	boolean isOpenTag(String input, int beginPos);
	boolean isClosingTag(String input, int beginPos);
}

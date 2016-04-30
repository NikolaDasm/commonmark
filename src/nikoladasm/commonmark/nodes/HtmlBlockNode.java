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

package nikoladasm.commonmark.nodes;

public class HtmlBlockNode extends BaseBlockNode implements BlockNode {

	private int htmlBlockType;
	private String literal;
	
	public HtmlBlockNode(int startLine, int startColumn) {
		super(startLine, startColumn);
	}

	public int htmlBlockType() {
		return htmlBlockType;
	}
	
	public void htmlBlockType(int type) {
		htmlBlockType = type;
	}

	public String literal() {
		return literal;
	}
	
	public void literal(String literal) {
		this.literal = literal;
	}
}

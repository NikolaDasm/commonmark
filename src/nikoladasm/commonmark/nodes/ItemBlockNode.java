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

public class ItemBlockNode extends BaseBlockNode implements BlockNode {

	private boolean ordered;
	private boolean tight;
	private char bulletChar;
	private int start;
	private char delimiter;
	private int padding;
	private int markerOffset;
	
	public ItemBlockNode(int startLine, int startColumn) {
		super(startLine, startColumn);
		tight = true;
	}
	
	public boolean isOrdered() {
		return ordered;
	}
	
	public void ordered(boolean ordered) {
		this.ordered = ordered;
	}
	
	public boolean tight() {
		return tight;
	}
	
	public void tight(boolean tight) {
		this.tight = tight;
	}
	
	public char bulletChar() {
		return bulletChar;
	}
	
	public void bulletChar(char bulletChar) {
		this.bulletChar = bulletChar;
	}

	public int start() {
		return start;
	}
	
	public void start(int start) {
		this.start = start;
	}
	
	public char delimiter() {
		return delimiter;
	}
	
	public void delimiter(char delimiter) {
		this.delimiter = delimiter;
	}
	
	public int padding() {
		return padding;
	}
	
	public void padding(int padding) {
		this.padding = padding;
	}

	public int markerOffset() {
		return markerOffset;
	}
	
	public void markerOffset(int offset) {
		markerOffset = offset;
	}
}

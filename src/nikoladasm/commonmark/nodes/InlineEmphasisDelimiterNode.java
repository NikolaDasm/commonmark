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

public class InlineEmphasisDelimiterNode extends BaseInlineNode implements InlineNode {

	private boolean active;
	private final char delimiter;
	private int numdelims;
	private final boolean beforeIsSpace;
	private final boolean beforeIsPunctuation;
	private boolean canOpen;
	private boolean canClose;
	
	public InlineEmphasisDelimiterNode(Node parent, char delimiter, boolean beforeIsSpace, boolean beforeIsPunctuation) {
		super(parent);
		active = true;
		this.delimiter = delimiter;
		numdelims = 1;
		this.beforeIsSpace = beforeIsSpace;
		this.beforeIsPunctuation = beforeIsPunctuation;
	}

	public void deactivate() {
		active = false;
	}
	
	public boolean isActive() {
		return active;
	}
	
	public char delimiter() {
		return delimiter;
	}
	
	public int numdelims() {
		return numdelims;
	}
	
	public void incNumdelims() {
		numdelims++;
	}
	
	public void decNumdelims() {
		numdelims--;
	}
	
	public void decBy2Numdelims() {
		numdelims-=2;
	}
	
	public boolean beforeIsSpace() {
		return beforeIsSpace;
	}
	
	public boolean beforeIsPunctuation() {
		return beforeIsPunctuation;
	}
	
	public boolean canOpen() {
		return canOpen;
	}
	
	public void canOpen(boolean canOpen) {
		this.canOpen = canOpen;
	}
	
	public boolean canClose() {
		return canClose;
	}

	public void canClose(boolean canClose) {
		this.canClose = canClose;
	}
}

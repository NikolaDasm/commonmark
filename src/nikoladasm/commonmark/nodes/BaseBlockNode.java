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

import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public abstract class BaseBlockNode implements BlockNode {

	private Deque<Node> childs = new LinkedList<>();
	private String stringContent = "";
	private Node parent;
	private Map<String,String> attr = new HashMap<>();
	private boolean open;
	private final int startLine;
	private final int startColumn;
	private int endLine;
	private int endColumn;
	private boolean lastLineBlank;

	protected BaseBlockNode(int startLine, int startColumn) {
		open = true;
		this.startLine = startLine;
		this.startColumn = startColumn;
	}
	
	@Override
	public Deque<Node> childs() {
		return childs;
	}

	@Override
	public void childs(Deque<Node> childs) {
		this.childs = childs;
	}
	
	@Override
	public Node parent() {
		return parent;
	}
	
	@Override
	public void parent (Node parent) {
		this.parent = parent;
	}
	
	@Override
	public String stringContent() {
		return stringContent;
	}

	@Override
	public void stringContent(String content) {
		stringContent = content;
	}

	@Override
	public Map<String,String> attr() {
		return attr;
	}
	@Override
	public boolean isOpen() {
		return open;
	}
	
	@Override
	public void close() {
		open = false;
	}
	
	@Override
	public int startLine() {
		return startLine;
	}
	
	@Override
	public int startColumn() {
		return startColumn;
	}
	
	@Override
	public void endLine(int lineNumber) {
		endLine = lineNumber;
	}
	
	@Override
	public int endLine() {
		return endLine;
	}
	
	@Override
	public void endColumn(int colomn) {
		endColumn = colomn;
	}
	
	@Override
	public int endColumn() {
		return endColumn;
	}

	@Override
	public boolean isLastLineBlank() {
		return lastLineBlank;
	}

	@Override
	public void lastLineBlank(boolean blank) {
		lastLineBlank = blank;
	}
}

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

public abstract class BaseInlineNode implements InlineNode {

	private Deque<Node> childs = new LinkedList<>();
	private Node parent;
	private Map<String,String> attr = new HashMap<>();
	
	protected BaseInlineNode() {
	}
	
	protected BaseInlineNode(Node parent) {
		this.parent = parent;
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
	public Map<String,String> attr() {
		return attr;
	}
}

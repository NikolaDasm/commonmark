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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import nikoladasm.commonmark.nodes.*;

import static nikoladasm.commonmark.Parser.State.*;

public class Parser {

	private static final int CODE_INDENT = 4;
	private static final String CDATA_LEAD = "CDATA[";
	private static final String CDATA_TRAIL = "]]>";
	
	private static final List<String> HTML_BLOCK_TYPE_1_START_TAGS =
		Arrays.asList("script", "pre", "style");
	private static final List<String> HTML_BLOCK_TYPE_6_TAGS =
		Arrays.asList("address", "article", "aside", "base",
			"basefont", "blockquote", "body", "caption", "center", "col",
			"colgroup", "dd", "details", "dialog", "dir", "div", "dl", "dt",
			"fieldset", "figcaption", "figure", "footer", "form",
			"frame", "frameset", "h1", "head", "header", "hr", "html",
			"iframe", "legend", "li", "link", "main", "menu", "menuitem",
			"meta", "nav", "noframes", "ol", "optgroup", "option", "p",
			"param", "section", "source", "summary", "table", "tbody",
			"td", "tfoot", "th", "thead", "title", "tr", "track", "ul");
	private static final String[] HTML_BLOCK_TYPE_1_ENDS =
		new String[]{"</script>", "</pre>", "</style>"};
	private static final String[] HTML_BLOCK_TYPE_FROM_2_TO_5_ENDS =
		new String[]{"-->", "?>", ">", CDATA_TRAIL};
		
	protected static enum State {
		UNKNOWN_CHAR,
		MAYBE_THEMATIC_BREAK,
		MAYBE_ATX_HEADING_LEAD,
		MAYBE_ATX_HEADING_BODY,
		MAYBE_SETEXT_HEADING,
		MAYBE_CODE_FENCE,
		MAYBE_FENCED_CODE_BLOCK,
		MAYBE_HTML_TAG,
		MAYBE_HTML_BLOCK_TYPE_2_OR_4_OR_5,
		MAYBE_HTML_BLOCK_TYPE_2,
		MAYBE_HTML_BLOCK_TYPE_5,
		MAYBE_HTML_BLOCK_TYPE_1_OR_6_OR_7,
		HTML_BLOCK,
		MAYBE_BULLET_LIST_ITEM_BLOCK,
		MAYBE_ORDERED_LIST_ITEM_BLOCK,
		LIST_ITEM_BLOCK,
		FOUND,
		NOT_FOUND
	}
	
	@FunctionalInterface
	private static interface ContinueChecker {
		boolean isContinue(BlockNode node);
	}
	
	@FunctionalInterface
	private static interface CanContainChecker {
		boolean canContain(BlockNode node); 
	}
	
	@FunctionalInterface
	private static interface BlockFinalizer {
		void finalizeBlock(BlockNode node);
	}
	
	@FunctionalInterface
	private static interface BlockStartHandler {
		void handle(BlockNode node);
	}
	
	public static Parser getInstance() {
		return new Parser(new InlineParserImpl(new CharReaderImpl()));
	}
	
	private InlineParser ip;
	private BlockNode root;
	private int lineNumber;
	private BlockNode current;
	private BlockNode oldCurrent;
	private String line;
	private int length;
	private int offset;
	private int column;
	private int nextNonspace;
	private int nextNonspaceColumn;
	private char c;
	private boolean blank;
	private int indent;
	private boolean indented;
	private boolean closedFence;
	private int lastLineLength;
	private boolean allClosed;
	private BlockNode lastMatched;
	private boolean matchedLeaf;
	private boolean partiallyConsumedTab;
	private State state;
	private int pos;
	private int newPos;
	private int recoveryPos;
	private char thematicBreackChar;
	private int thematicBreackCharCount;
	private int atxHeadingMarkersCount;
	private StringBuilder sb = new StringBuilder();
	private int atxHeadingBeginPos;
	private int atxHeadingEndPos;
	private boolean atxHeadingBegin;
	private boolean atxHeadingEnd;
	private boolean atxHeadingTrailBegin;
	private boolean atxHeadingTrailEnd;
	private char setextHeadingChar;
	private boolean setextHeadingEnd;
	private char fencedCodeBlockChar;
	private int fencedCodeBlockCharCount;
	private boolean infoStringBegin;
	private int htmlBlockType;
	private int cdataLeadIndex;
	private List<String> htmlBlockType1Tags = new LinkedList<>();
	private List<String> htmlBlockType6Tags = new LinkedList<>();
	private Iterator<String> htmlBlockType1TagsIterator;
	private Iterator<String> htmlBlockType6TagsIterator;
	private char previousChar;
	private char currentChar;
	private int tagsIndex;
	private boolean isClosingTag;
	private int tagBeginPos;
	private char bulletChar;
	private boolean orderedList;
	private int start;
	private int listItemDigitCount;
	private char delimiter;
	private int markerOffset;
	private int padding;
	
	private Map<Class<? extends BlockNode>,ContinueChecker> continueCheckers;
	private Map<Class<? extends BlockNode>,CanContainChecker> canContainCheckers;
	private Map<Class<? extends BlockNode>,BlockFinalizer> blockFinalizers;
	private Map<State,BlockStartHandler> blockStartHandlers;
	
	public Parser(InlineParser ip) {
		this.ip = ip;
		initContinueHandlers();
	}
	
	private void initContinueHandlers() {
		continueCheckers = new HashMap<>();
		continueCheckers.put(DocumentNode.class, (node) -> true);
		continueCheckers.put(ThematicBreakNode.class, (node) -> false);
		continueCheckers.put(ParagraphBlockNode.class, (node) -> !blank);
		continueCheckers.put(HeadingBlockNode.class, (node) -> false);
		continueCheckers.put(CodeBlockNode.class, this::codeBlockContinueChecker);
		continueCheckers.put(HtmlBlockNode.class, (node) -> {
			int type = ((HtmlBlockNode) node).htmlBlockType();
			return !(blank && (type == 6 || type == 7));
		});
		continueCheckers.put(BlockQuoteNode.class, this::blockQuoteContinueChecker);
		continueCheckers.put(ListBlockNode.class, (node) -> true);
		continueCheckers.put(ItemBlockNode.class, this::itemBlockContinueChecker);
		canContainCheckers = new HashMap<>();
		CanContainChecker isNotItemBlockNode = node -> !(node instanceof ItemBlockNode);
		canContainCheckers.put(DocumentNode.class, isNotItemBlockNode);
		canContainCheckers.put(BlockQuoteNode.class, isNotItemBlockNode);
		canContainCheckers.put(ListBlockNode.class, node -> (node instanceof ItemBlockNode));
		canContainCheckers.put(ItemBlockNode.class, isNotItemBlockNode);
		blockFinalizers = new HashMap<>();
		blockFinalizers.put(ParagraphBlockNode.class, this::finalizeParagraphBlock);
		blockFinalizers.put(CodeBlockNode.class, this::finalizeCodeBlock);
		blockFinalizers.put(HtmlBlockNode.class, this::finalizeHtmlBlock);
		blockFinalizers.put(ListBlockNode.class, this::finalizeListBlock);
		blockStartHandlers = new HashMap<>();
		blockStartHandlers.put(UNKNOWN_CHAR, this::unknownCharHandler);
		blockStartHandlers.put(MAYBE_THEMATIC_BREAK, this::maybeThematicBreakHandler);
		blockStartHandlers.put(MAYBE_ATX_HEADING_LEAD, this::maybeAtxHeadingLeadHandler);
		blockStartHandlers.put(MAYBE_ATX_HEADING_BODY, this::meybeAtxHeadingBodyHandler);
		blockStartHandlers.put(MAYBE_SETEXT_HEADING, this::maybeSetextHeadingHandler);
		blockStartHandlers.put(MAYBE_CODE_FENCE, this::maybeCodeFenceHandler);
		blockStartHandlers.put(MAYBE_FENCED_CODE_BLOCK, this::maybeFencedCodeBlockHandler);
		blockStartHandlers.put(MAYBE_HTML_TAG, this::maybeHtmlTagHandler);
		blockStartHandlers.put(MAYBE_HTML_BLOCK_TYPE_2_OR_4_OR_5, this::maybeHtmlBlockType2Or4Or5Handler);
		blockStartHandlers.put(MAYBE_HTML_BLOCK_TYPE_2, this::maybeHtmlBlockType2Handler);
		blockStartHandlers.put(MAYBE_HTML_BLOCK_TYPE_5, this::maybeHtmlBlockType5Handler);
		blockStartHandlers.put(MAYBE_HTML_BLOCK_TYPE_1_OR_6_OR_7, this::maybeHtmlBlockType1Or6Or7Handler);
		blockStartHandlers.put(HTML_BLOCK, this::htmlBlockHandler);
		blockStartHandlers.put(MAYBE_BULLET_LIST_ITEM_BLOCK, this::maybeBulletListItemBlockHandler);
		blockStartHandlers.put(MAYBE_ORDERED_LIST_ITEM_BLOCK, this::maybeOrderedListItemBlockHandler);
		blockStartHandlers.put(LIST_ITEM_BLOCK, this::listItemBlockHandler);
	}
	
	public Node parse(String input) {
		return parse(new StringReader(input));
	}
	
	public Node parse(Reader reader) {
		ip.refmap().clear();
		ip.recreateStringBuilders();
		sb = new StringBuilder();
		root = new DocumentNode(0, 0);
		BufferedReader bufferedReader;
		if (reader instanceof BufferedReader)
			bufferedReader = (BufferedReader) reader;
		else
			bufferedReader = new BufferedReader(reader);
		String line;
		try {
			current = root;
			lineNumber = 0;
			lastLineLength = 0;
			lastMatched = root;
			while ((line = bufferedReader.readLine()) != null) {
				lineNumber++;
				processLine(line);
			}
			while (current != null)
				finalizeBlock(current, lineNumber);
			processInlines();
			return root;
		} catch (IOException e) {
			throw new CommonMarkParserException("Can't read input", e);
		}
	}
	
	private char readChar(int pos) {
		char c = line.charAt(pos);
		return c == 0 ? '\uFFFD' : c;
	}
	
	private void findNextNonspace() {
		nextNonspace = offset;
		nextNonspaceColumn = column;
		while (nextNonspace < length) {
			c = readChar(nextNonspace);
			if (c == ' ') {
				nextNonspace++;
				nextNonspaceColumn++;
			} else if (c == '\t') {
				nextNonspace++;
				nextNonspaceColumn += 4 - (nextNonspaceColumn % 4);
			} else {
				break;
			}
		}
		blank = nextNonspace == length;
		indent = nextNonspaceColumn - column;
		indented = indent >= CODE_INDENT;
	}
	
	private void advanceNextNonspace() {
		offset = nextNonspace;
		column = nextNonspaceColumn;
		partiallyConsumedTab = false;
	}
	
	private boolean endsWithBlankLine(BlockNode node) {
		while (node != null) {
			if (node.isLastLineBlank())
				return true;
			if ((node instanceof ListBlockNode) ||
				(node instanceof ItemBlockNode))
				node = (BlockNode) node.childs().peekLast();
			else
				break;
		}
		return false;
	}
	
	private boolean codeBlockContinueChecker(BlockNode node) {
		if (((CodeBlockNode)node).isFenced()) {
			CodeBlockNode cNode = (CodeBlockNode)node;
			boolean closingCodeFence = true;
			if (closingCodeFence && indent >= CODE_INDENT)
				closingCodeFence = false;
			char fenceChar = cNode.fenceChar();
			if (nextNonspace >= length ||
				readChar(nextNonspace) != fenceChar)
				closingCodeFence = false;
			if (closingCodeFence) {
				int fanceLength = 0;
				for (int i = nextNonspace; i < length; i++) {
					if (readChar(i) != fenceChar) break;
					fanceLength++;
				}
				if (fanceLength < cNode.fenceLength())
					closingCodeFence = false;
				for (int i = nextNonspace + fanceLength; i < length; i++)
					if (readChar(i) != ' ') closingCodeFence = false;
			}
			if (closingCodeFence) {
				finalizeBlock(node, lineNumber);
				closedFence = true;
			} else {
				int fenceOffset = cNode.fenceOffset();
				int end = (offset + fenceOffset < length) ? offset + fenceOffset : length;
				char c;
				for (pos = offset; pos < end; pos++) {
					c = readChar(pos);
					if (c != ' ' && c != '\t') break;
				}
				if (pos - offset > 0)
					advanceOffset(pos - offset, true);
			}
		} else {
			if (indent >= CODE_INDENT)
				advanceOffset(CODE_INDENT, true);
			else if (blank) {
				advanceNextNonspace();
			} else {
				return false;
			}
		}
		return true;
	}
	
	private boolean blockQuoteContinueChecker(BlockNode node) {
		if (!indented && nextNonspace < length &&
			readChar(nextNonspace) == '>') {
			advanceNextNonspace();
			advanceOffset(1, false);
			c = offset < length ? readChar(offset) : 0;
			if (c == ' ' || c == '\t') advanceOffset(1, true);
			return true;
		} else {
			return false;
		}
	}

	private boolean itemBlockContinueChecker(BlockNode node) {
		ItemBlockNode iNode = (ItemBlockNode) node;
		if (blank) {
			if (node.childs().isEmpty())
				return false;
			else
				advanceNextNonspace();
		} else if (indent >= iNode.markerOffset() + iNode.padding()) {
			advanceOffset(iNode.markerOffset() + iNode.padding(), true);
		} else {
			return false;
		}
		return true;
	}
	
	private boolean acceptsLines(BlockNode node) {
		return (node instanceof ParagraphBlockNode) ||
			(node instanceof CodeBlockNode) ||
			(node instanceof HtmlBlockNode);
	}
	
	private void finalizeParagraphBlock(BlockNode node) {
		String stringContent = node.stringContent();
		int pos = ip.parseReferences(stringContent);
		if (pos > 0) {
			char c = 0;
			int length = stringContent.length();
			for (int i = pos; i < length; i++) {
				c = stringContent.charAt(i);
				if (!(c == ' ' || c == '\t' || c == '\f' ||
					c == '\u000B' || c == '\r' || c == '\n')) {
					node.stringContent(stringContent.substring(pos));
					return;
				}
			}
			removeNode(node);
		}
	}
	
	private void finalizeCodeBlock(BlockNode node) {
		if (((CodeBlockNode)node).isFenced()) {
			CodeBlockNode cNode = (CodeBlockNode)node;
			String content = cNode.stringContent();
			int newlinePos = content.indexOf('\n');
			String firstLine = content.substring(0, newlinePos);
			String rest = content.substring(newlinePos + 1);
			cNode.info(ip.unescapeString(firstLine.trim()));
			cNode.literal(rest);
		} else {
			String line = node.stringContent();
			int length = line.length();
			int end = length;
			for (int i = length-1; i >= 0; i--) {
				c = line.charAt(i);
				if (c != ' ' && c != '\n') break;
				if (c == '\n') end = i;
			}
			end++;
			if (end < length)
				((CodeBlockNode) node).literal(line.substring(0, end));
			else
				((CodeBlockNode) node).literal(line);
		}
		node.stringContent(null);
	}

	private void finalizeHtmlBlock(BlockNode node) {
		String line = node.stringContent();
		int length = line.length();
		int end = length;
		for (int i = length-1; i >= 0; i--) {
			c = line.charAt(i);
			if (c != ' ' && c != '\n') break;
			if (c == '\n') end = i;
		}
		if (end < length)
			((HtmlBlockNode) node).literal(line.substring(0, end));
		else
			((HtmlBlockNode) node).literal(line);
		node.stringContent(null);
	}
	
	private void finalizeListBlock(BlockNode node) {
		Node last = node.childs().peekLast();
		for (Node iNode : node.childs()) {
			if (endsWithBlankLine((BlockNode) iNode) &&
				iNode != last) {
				((ListBlockNode) node).tight(false);
				break;
			}
			Node subLast = iNode.childs().peekLast();
			for (Node subiNode : iNode.childs()) {
				if (endsWithBlankLine((BlockNode) subiNode) &&
					(iNode != last || subiNode != subLast)) {
					((ListBlockNode) node).tight(false);
					break;
				}
			}
		}
	}

	private void finalizeBlock(BlockNode block, int lineNumber) {
		BlockNode above = (BlockNode) block.parent();
		block.close();
		block.endLine(lineNumber);
		block.endColumn(lastLineLength);
		BlockFinalizer finalizer = blockFinalizers.get(block.getClass());
		if (finalizer != null) finalizer.finalizeBlock(block);
		current = above;
	}

	private void addChild(BlockNode child) {
		CanContainChecker checker;
		while (current != null &&
			((checker = canContainCheckers.get(current.getClass())) == null ||
			!checker.canContain(child)))
			finalizeBlock(current, lineNumber - 1);
		child.parent(current);
		current.childs().addLast(child);
		current = child;
	}

	private void closeUnmatchedBlocks() {
		if (!allClosed) {
			while (oldCurrent != lastMatched) {
				BlockNode parent = (BlockNode) oldCurrent.parent();
				finalizeBlock(oldCurrent, lineNumber - 1);
				oldCurrent = parent;
			}
			allClosed = true;
		}
	}
	
	private void addLine() {
		sb.setLength(0);
		if (partiallyConsumedTab) {
			offset++;
			int charsToTab = 4 - (column % 4);
			for(int i=0; i<charsToTab;i++) sb.append(' ');
		}
		for (int i = offset; i < length; i++) {
			sb.append(readChar(i));
		}
		sb.append('\n');
		current.stringContent(current.stringContent()+sb.toString());
	}

	private void advanceOffset(int count, boolean columns) {
		int charsToTab, charsToAdvance;
		while (count > 0 && offset < length) {
			c = readChar(offset);
			if (c == '\t') {
				charsToTab = 4 - (column % 4);
				partiallyConsumedTab = columns && charsToTab > count;
				charsToAdvance = charsToTab > count ? count : charsToTab;
				column += columns ? charsToAdvance : charsToTab;
				offset += (!columns || columns && !partiallyConsumedTab) ? 1 : 0;
				count -= (columns ? charsToAdvance : 1);
			} else {
				partiallyConsumedTab = false;
				offset++;
				column++;
				count--;
			}
		}
	}
	
	private void breakOutOfLists(BlockNode node) {
		Node b = node;
		ListBlockNode lastList = null;
		 do {
			 if (b instanceof ListBlockNode)
				 lastList = (ListBlockNode) b;
			 b = b.parent();
		 } while (b != null);
		 if (lastList != null) {
			 while (node != lastList) {
				 finalizeBlock(node, lineNumber);
				 node = (BlockNode) node.parent();
			 }
			 finalizeBlock(lastList, lineNumber);
			 current = (BlockNode) lastList.parent();
		 }
	}
	
	private void removeNode(Node node) {
		Deque<Node> childs = new LinkedList<>();
		Node parent = node.parent();
		parent.childs().forEach(n -> {
			if (n != node) childs.addLast(n);
		});
		parent.childs(childs);
	}
	
	private void replaceNode(Node node, Node newNode) {
		Deque<Node> childs = new LinkedList<>();
		Node parent = node.parent();
		parent.childs().forEach(n -> {
			if (n != node)
				childs.addLast(n);
			else
				childs.addLast(newNode);
		});
		parent.childs(childs);
	}
	
	private void unknownCharHandler(BlockNode node) {
		if (!indented &&
			(node instanceof ParagraphBlockNode) &&
			(c == '-' || c == '=')) {
			setextHeadingChar = c;
			setextHeadingEnd = false;
			newPos = pos;
			state = MAYBE_SETEXT_HEADING;
		} else if (!indented &&
			(c == '*' || c == '-' || c == '_')) {
			thematicBreackChar = c;
			thematicBreackCharCount = 0;
			newPos = pos;
			state = MAYBE_THEMATIC_BREAK;
		} else if ((!indented || (node instanceof ListBlockNode)) &&
			(c == '*' || c == '+' || c == '-' || c >= '0' && c <= '9')) {
			if (c >= '0' && c <= '9') {
				orderedList = true;
				start = c - '0';
				listItemDigitCount = 1;
				delimiter = 0;
				bulletChar = 0;
				state = MAYBE_ORDERED_LIST_ITEM_BLOCK;
			} else {
				delimiter = 0;
				bulletChar = 0;
				orderedList = false;
				newPos = pos;
				state = MAYBE_BULLET_LIST_ITEM_BLOCK;
			}
		} else if (!indented && c == '#') {
			atxHeadingMarkersCount = 0;
			state = MAYBE_ATX_HEADING_LEAD;
			newPos = pos;
		} else if (!indented && (c == '`' || c == '~')) {
			state = MAYBE_CODE_FENCE;
			fencedCodeBlockChar = c;
			fencedCodeBlockCharCount = 1;
		} else if (!indented && c == '<') {
			isClosingTag = false;
			state = MAYBE_HTML_TAG;
		} else if (!indented && c == '>') {
			advanceNextNonspace();
			advanceOffset(1, false);
			c = offset < length ? readChar(offset) : 0;
			if (c == ' ' || c == '\t') advanceOffset(1, true);
			closeUnmatchedBlocks();
			BlockNode child = new BlockQuoteNode(lineNumber, nextNonspace+1);
			addChild(child);
			state = FOUND;
		} else if (indented &&
			!(current instanceof ParagraphBlockNode) && !blank) {
			advanceOffset(CODE_INDENT, true);
			closeUnmatchedBlocks();
			BlockNode child = new CodeBlockNode(lineNumber, offset+1);
			addChild(child);
			matchedLeaf = true;
			state = FOUND;
		} else {
			state = NOT_FOUND;
		}
		recoveryPos = pos+1;
	}
	
	private void maybeSetextHeadingHandler(BlockNode node) {
		if (setextHeadingEnd && c != ' ' ||
			!setextHeadingEnd && c != ' ' && c != setextHeadingChar) {
			if (setextHeadingChar == '-') {
				newPos = recoveryPos;
				state = MAYBE_THEMATIC_BREAK;
				thematicBreackChar = setextHeadingChar;
				thematicBreackCharCount = 1;
			} else {
				state = NOT_FOUND;
			}
			return;
		} else if (!setextHeadingEnd && c == ' ') {
			setextHeadingEnd = true;
		}
		if (newPos >= length) {
			closeUnmatchedBlocks();
			HeadingBlockNode child = new HeadingBlockNode(node.startLine(), node.startColumn());
			child.level(setextHeadingChar == '=' ? 1 : 2);
			child.stringContent(node.stringContent());
			child.parent(node.parent());
			replaceNode(node, child);
			this.current = child;
			advanceOffset(length - offset, false);
			matchedLeaf = true;
			state = FOUND;
		}
	}
	
	private void maybeThematicBreakHandler(BlockNode node) {
		if ((c != ' ' && c != thematicBreackChar) ||
			newPos >= length &&
			thematicBreackCharCount < (c == thematicBreackChar ? 2 : 3)) {
			if (thematicBreackChar == '-' || thematicBreackChar == '*') {
				delimiter = 0;
				bulletChar = 0;
				orderedList = false;
				newPos = recoveryPos-1;
				state = MAYBE_BULLET_LIST_ITEM_BLOCK;
			} else {
				state = NOT_FOUND;
			}
			return;
		} else if (c == thematicBreackChar) {
			thematicBreackCharCount++;
		}
		if (newPos >= length && thematicBreackCharCount >= 3) {
			closeUnmatchedBlocks();
			BlockNode child = new ThematicBreakNode(lineNumber, nextNonspace+1);
			addChild(child);
			advanceOffset(length - offset, false);
			matchedLeaf = true;
			state = FOUND;
		}
	}
	
	private void maybeAtxHeadingLeadHandler(BlockNode node) {
		if (c == '#' && atxHeadingMarkersCount < 6 && newPos < length) {
			atxHeadingMarkersCount++;
		} else if (c == ' ' ||
			(newPos >= length && atxHeadingMarkersCount < (c == '#' ? 5 : 6))) {
			if (newPos >= length && c == '#') atxHeadingMarkersCount++;
			sb.setLength(0);
			state = MAYBE_ATX_HEADING_BODY;
			newPos = pos;
			atxHeadingBeginPos = pos;
			atxHeadingEndPos = length;
			atxHeadingTrailBegin = false;
			atxHeadingTrailEnd = false;
			atxHeadingBegin = false;
			atxHeadingEnd = false;
		} else {
			state = NOT_FOUND;
		}
	}
	
	private void meybeAtxHeadingBodyHandler(BlockNode node) {
		if (atxHeadingTrailEnd && c != ' ') {
			atxHeadingTrailEnd = false;
			atxHeadingTrailBegin = false;
			atxHeadingEndPos = length;
			atxHeadingEnd = false;
		}
		if (atxHeadingTrailBegin && c == ' ')
			atxHeadingTrailEnd = true;
		if (atxHeadingTrailBegin && !atxHeadingTrailEnd && c != '#') {
			atxHeadingTrailBegin = false;
			atxHeadingEndPos = length;
			atxHeadingEnd = false;
		}
		if (atxHeadingEnd && !atxHeadingTrailBegin && c == '#')
			atxHeadingTrailBegin = true;
		if (atxHeadingEnd && !atxHeadingTrailBegin && c != ' ' && c != '#')
			atxHeadingEnd = false;
		if (atxHeadingBegin && !atxHeadingEnd && c == ' ') {
			atxHeadingEnd = true;
			atxHeadingEndPos = pos;
		}
		if (!atxHeadingBegin && c != ' ' && c != '#')
			atxHeadingBegin = true;
		if (newPos >= length) {
			if (newPos >= length) sb.append(c);
			if (!atxHeadingTrailBegin) atxHeadingEndPos = length;
			advanceNextNonspace();
			advanceOffset(atxHeadingMarkersCount, false);
			closeUnmatchedBlocks();
			HeadingBlockNode child = new HeadingBlockNode(lineNumber, nextNonspace+1);
			child.stringContent(atxHeadingBegin ? atxHeadingEndPos >= length ?
				sb.toString() : sb.substring(0, atxHeadingEndPos-atxHeadingBeginPos+1) : "");
			child.level(atxHeadingMarkersCount);
			addChild(child);
			advanceOffset(length - offset, false);
			matchedLeaf = true;
			state = FOUND;
		}
		sb.append(c);
	}
	
	private void maybeCodeFenceHandler(BlockNode node) {
		if (c == fencedCodeBlockChar)
			fencedCodeBlockCharCount++;
		if ((c != fencedCodeBlockChar || newPos >= length)
			&& fencedCodeBlockCharCount >= 3) {
			if (newPos >= length) newPos = pos;
			state = MAYBE_FENCED_CODE_BLOCK;
			infoStringBegin = false;
			return;
		}
		if (c != fencedCodeBlockChar && fencedCodeBlockCharCount < 3)
			state = NOT_FOUND;
	}
	
	private void maybeFencedCodeBlockHandler(BlockNode node) {
		if (c == fencedCodeBlockChar &&
			(newPos < length || infoStringBegin)) {
			state = NOT_FOUND;
			return;
		}
		if (!infoStringBegin &&
			newPos < length && c != fencedCodeBlockChar)
			infoStringBegin = true;
		if (newPos >= length) {
			closeUnmatchedBlocks();
			CodeBlockNode child = new CodeBlockNode(lineNumber, nextNonspace+1);
			child.fenced(true);
			child.fenceLength(fencedCodeBlockCharCount);
			child.fenceChar(fencedCodeBlockChar);
			child.fenceOffset(indent);
			addChild(child);
			advanceNextNonspace();
			advanceOffset(fencedCodeBlockCharCount, false);
			matchedLeaf = true;
			state = FOUND;
		}
	}
	
	private void maybeHtmlTagHandler(BlockNode node) {
		if (!isClosingTag && c == '?') {
			htmlBlockType = 3;
			state = HTML_BLOCK;
		} else if (!isClosingTag && c == '!') {
			state = MAYBE_HTML_BLOCK_TYPE_2_OR_4_OR_5;
		} else if (!isClosingTag && c == '/') {
			isClosingTag = true;
		} else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
			htmlBlockType1Tags.clear();
			if (!isClosingTag) {
				HTML_BLOCK_TYPE_1_START_TAGS.forEach(tag -> {
					if ((c | 0x20) == tag.charAt(0)) htmlBlockType1Tags.add(tag);
				});
			}
			htmlBlockType6Tags.clear();
			HTML_BLOCK_TYPE_6_TAGS.forEach(tag -> {
				if ((c | 0x20) == tag.charAt(0)) htmlBlockType6Tags.add(tag);
			});
			tagsIndex = 0;
			tagBeginPos = pos;
			newPos = pos;
			currentChar = previousChar;
			state = MAYBE_HTML_BLOCK_TYPE_1_OR_6_OR_7;
		} else {
			state = NOT_FOUND;
		}
	}
	
	private void maybeHtmlBlockType2Or4Or5Handler(BlockNode node) {
		if (c == '-') {
			state = MAYBE_HTML_BLOCK_TYPE_2;
		} else if (c == '[') {
			cdataLeadIndex = 0;
			state = MAYBE_HTML_BLOCK_TYPE_5;
		} else if (c >= 'A' && c <= 'Z') {
			htmlBlockType = 4;
			state = HTML_BLOCK;
			newPos = pos;
		} else {
			state = NOT_FOUND;
		}
	}
	
	private void maybeHtmlBlockType2Handler(BlockNode node) {
		if (c == '-') {
			htmlBlockType = 2;
			state = HTML_BLOCK;
			newPos = pos;
		} else {
			state = NOT_FOUND;
		}
	}
	
	private void maybeHtmlBlockType5Handler(BlockNode node) {
		if (c != CDATA_LEAD.charAt(cdataLeadIndex)) {
			state = NOT_FOUND;
			return;
		}
		cdataLeadIndex++;
		if (cdataLeadIndex == 6) {
			htmlBlockType = 5;
			state = HTML_BLOCK;
			newPos = pos;
		}
	}
	
	private void maybeHtmlBlockType1Or6Or7Handler(BlockNode node) {
		htmlBlockType1TagsIterator = htmlBlockType1Tags.iterator();
		while (htmlBlockType1TagsIterator.hasNext()) {
			String tag = htmlBlockType1TagsIterator.next();
			int length = tag.length();
			if (tagsIndex < length) {
				if ((c | 0x20) != tag.charAt(tagsIndex) ||
					newPos >= this.length && tagsIndex != length-1)
					htmlBlockType1TagsIterator.remove();
			} else {
				if (tagsIndex == length && !(ip.isWhitespase(c) || c == '>'))
					htmlBlockType1TagsIterator.remove();
			}
		}
		htmlBlockType6TagsIterator = htmlBlockType6Tags.iterator();
		while (htmlBlockType6TagsIterator.hasNext()) {
			String tag = htmlBlockType6TagsIterator.next();
			int length = tag.length();
			if (tagsIndex < length) {
				if ((c | 0x20) != tag.charAt(tagsIndex) ||
					newPos >= this.length && tagsIndex != length-1)
					htmlBlockType6TagsIterator.remove();
			} else {
				if ((tagsIndex == length && !(ip.isWhitespase(c) || c == '>' || c == '/'))||
					(tagsIndex == length+1 && !(previousChar == '/' && c == '>')))
					htmlBlockType6TagsIterator.remove();
			}
		}
		if (htmlBlockType6Tags.size() == 1 &&
			(ip.isWhitespase(c) || c == '>' || newPos >= this.length)) {
			htmlBlockType = 6;
			state = HTML_BLOCK;
			newPos = pos;
		} else if (htmlBlockType1Tags.size() == 1 &&
			(ip.isWhitespase(c) || c == '>' || newPos >= this.length)) {
			htmlBlockType = 1;
			state = HTML_BLOCK;
			newPos = pos;
		} else if (htmlBlockType6Tags.isEmpty() && htmlBlockType1Tags.isEmpty() &&
			!(node instanceof ParagraphBlockNode) &&
			(isClosingTag ? ip.isClosingTag(line, tagBeginPos) : ip.isOpenTag(line, tagBeginPos))) {
			htmlBlockType = 7;
			state = HTML_BLOCK;
			newPos = pos;
		}
		tagsIndex++;
	}
	
	private void htmlBlockHandler(BlockNode node) {
		closeUnmatchedBlocks();
		HtmlBlockNode child = new HtmlBlockNode(lineNumber, offset+1);
		child.htmlBlockType(htmlBlockType);
		addChild(child);
		matchedLeaf = true;
		state = FOUND;
	}
	
	private void maybeBulletListItemBlockHandler(BlockNode node) {
		if (bulletChar != 0 && !(c == ' ' || c == '\t')) {
			state = NOT_FOUND;
			return;
		}
		if (bulletChar != 0 || newPos >= length) {
			if (bulletChar == 0 && newPos >= length) bulletChar = c;
			newPos = pos;
			state = LIST_ITEM_BLOCK;
		}
		if (bulletChar == 0) bulletChar = c;
	}
	
	private void maybeOrderedListItemBlockHandler(BlockNode node) {
		if (delimiter == 0 && !(c >= '0' && c <= '9' || c == '.' || c == ')') ||
			delimiter != 0 && c != ' ' && c != '\t' ||
			listItemDigitCount > 9 ||
			newPos >= length && delimiter == 0 && c != '.' && c != ')') {
			state = NOT_FOUND;
			return;
		} else if (c == '.' || c == ')') {
			delimiter = c;
		} else if (c >= '0' && c <= '9') {
			listItemDigitCount++;
			start = start * 10 + c - '0';
		}
		if (c == ' ' || c == '\t' || newPos >= length) {
			newPos = pos;
			state = LIST_ITEM_BLOCK;
		}
	}
	
	private boolean listsMatch(ListBlockNode node) {
		return (node.isOrdered() == orderedList &&
			node.delimiter() == delimiter &&
			node.bulletChar() == bulletChar);
	}
	
	private void listItemBlockHandler(BlockNode node) {
		markerOffset = indent;
		advanceNextNonspace();
		advanceOffset(orderedList ? listItemDigitCount+1 : 1, true);
		int spacesStartCol = column;
		int spacesStartOffset = offset;
		char c;
		do {
			advanceOffset(1, true);
			c = offset < length ? readChar(offset) : 0;
		} while (column - spacesStartCol < 5 && (c == ' ' | c == '\t'));
		boolean blankItem = offset >= length;
		int spaces_after_marker = column - spacesStartCol;
		if (spaces_after_marker >= 5 ||
			spaces_after_marker < 1 ||
			blankItem) {
			padding = (orderedList ? listItemDigitCount+1 : 1) + 1;
			column = spacesStartCol;
			offset = spacesStartOffset;
			if (offset < length && ((c = readChar(offset)) == ' ' || c == '\t'))
				advanceOffset(1, true);
		} else {
			padding = (orderedList ? listItemDigitCount+1 : 1) + spaces_after_marker;
		}
		closeUnmatchedBlocks();
		if (!(current instanceof ListBlockNode) ||
			!listsMatch((ListBlockNode) current)) {
			ListBlockNode child = new ListBlockNode(lineNumber, nextNonspace+1);
			child.ordered(orderedList);
			child.bulletChar(bulletChar);
			child.start(start);
			child.delimiter(delimiter);
			child.padding(padding);
			child.markerOffset(markerOffset);
			addChild(child);
		}
		ItemBlockNode child = new ItemBlockNode(lineNumber, nextNonspace+1);
		child.ordered(orderedList);
		child.bulletChar(bulletChar);
		child.start(start);
		child.delimiter(delimiter);
		child.padding(padding);
		child.markerOffset(markerOffset);
		addChild(child);
		state = FOUND;
	}
	
	private boolean checkBlockStart(BlockNode node) {
		matchedLeaf = false;
		pos = nextNonspace;
		state = UNKNOWN_CHAR;
		while (state != NOT_FOUND) {
			c = pos >= length ? 0 : readChar(pos);
			currentChar = c;
			newPos = pos+1;
			blockStartHandlers.get(state).handle(node);
			if (state == FOUND) return true;
			pos = newPos;
			previousChar = currentChar;
			if (pos >= length) state = NOT_FOUND;
		}
		return false;
	}
	
	private void processLine(String line) {
		this.line = line;
		length = line.length();
		offset = 0;
		column = 0;
		partiallyConsumedTab = false;
		blank = false;
		oldCurrent = current;
		closedFence = false;
		BlockNode current = root;
		BlockNode lastChild = root;
		while ((lastChild = (BlockNode) lastChild.childs().peekLast()) != null &&
			lastChild.isOpen()) {
			findNextNonspace();
			if (continueCheckers.get(lastChild.getClass()).isContinue(lastChild)) {
				if (closedFence) {
					lastLineLength = length;
					return;
				}
			} else {
				break;
			}
			current = lastChild;
		}
		allClosed = current == oldCurrent;
		lastMatched = current;
		if (blank && current.isLastLineBlank()) {
			breakOutOfLists(current);
			current = this.current;
		}
		boolean matchedLeaf = !(current instanceof ParagraphBlockNode) &&
			acceptsLines(current);
		while (!matchedLeaf) {
			findNextNonspace();
			if (!indented &&
				!("#`~*+_=<>-".indexOf(c) >= 0 || (c >= '0' && c <= '9'))) {
				advanceNextNonspace();
				break;
			}
			if (checkBlockStart(current)) {
				current = this.current;
				matchedLeaf = this.matchedLeaf;
			} else {
				advanceNextNonspace();
				break;
			}
		}
		if (!allClosed && !blank &&
			(this.current instanceof ParagraphBlockNode)) {
			addLine();
		} else {
			closeUnmatchedBlocks();
			if (blank && current.childs().peekLast() != null)
				((BlockNode) current.childs().peekLast()).lastLineBlank(true);
			boolean lastLineBlank = blank &&
				!((current instanceof BlockQuoteNode) ||
				((current instanceof CodeBlockNode) && ((CodeBlockNode) current).isFenced()) ||
				(current instanceof ItemBlockNode &&
					current.childs().peekFirst() == null &&
					current.startLine() == lineNumber));
			BlockNode cont = current;
			while (cont != null) {
				cont.lastLineBlank(lastLineBlank);
				cont = (BlockNode) cont.parent();
			}
			if (acceptsLines(current)) {
				addLine();
				int type;
				if ((current instanceof HtmlBlockNode) &&
					(type = ((HtmlBlockNode) current).htmlBlockType()) >= 1 && type <= 5) {
					boolean tagsEndFound = false;
					if (type == 1) {
						line = this.line.substring(offset).toLowerCase();
						for (int i = 0; i < HTML_BLOCK_TYPE_1_ENDS.length; i++)
							if (line.contains(HTML_BLOCK_TYPE_1_ENDS[i]))
								tagsEndFound = true;
					} else {
						line = this.line.substring(offset);
						if (line.contains(HTML_BLOCK_TYPE_FROM_2_TO_5_ENDS[type-2]))
							tagsEndFound = true;
					}
					if (tagsEndFound) finalizeBlock(current, lineNumber);
				}
			} else if (offset < length && !blank) {
				BlockNode node = new ParagraphBlockNode(lineNumber, offset+1);
				addChild(node);
				advanceNextNonspace();
				addLine();
			}
		}
		lastLineLength = length;
	}
	
	private void processInlines() {
		if (root == null) return;
		Node current = root;
		Deque<Iterator<Node>> stack = new LinkedList<>();
		boolean finished = false;
		while (!finished) {
			while (current != null) {
				if ((current instanceof ParagraphBlockNode) ||
					(current instanceof HeadingBlockNode)) {
					ip.parseInline((BlockNode) current);
					((BlockNode)current).stringContent(null);
				}
				if (current.childs().isEmpty()) break;
				Iterator<Node> iterator = current.childs().iterator();
				stack.push(iterator);
				current = iterator.next();
			}
			Iterator<Node> iterator = stack.peek();
			if (iterator != null && iterator.hasNext()) {
				current = iterator.next();
			} else if (iterator == null) {
				finished = true;
				break;
			} else {
				stack.pop();
				current = null;
			}
		}
	}
}

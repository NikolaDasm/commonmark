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

import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.Function;

import nikoladasm.commonmark.nodes.*;

import static nikoladasm.commonmark.UrlUtil.*;
import static nikoladasm.commonmark.InlineParserImpl.State.*;

public class InlineParserImpl implements InlineParser {
	private static final String WHITESPACE_CHARACTERS = " \t\n\u000B\f\r";
	private static final String ASCII_PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";
	private static final Function<Character,Boolean> DEFAULT_WHITESPACE_CHAR_FUNC =
		c -> WHITESPACE_CHARACTERS.indexOf(c) >= 0;

	public static final String CDATA_LEAD = "CDATA[";
	public static final String CDATA_TRAIL = "]]>";

	@FunctionalInterface
	private static interface InlineParserHandler {
		void handle();
	}
	
	protected static enum State {
		UNKNOWN_CHAR,
		MAYBE_LEFT_BACKTICK_STRING,
		MAYBE_CODE_SPAN_BODY,
		MAYBE_RIGHT_BACKTICK_STRING,
		MAYBE_AUTOLINK_OR_HTML_TAG,
		MAYBE_EMAIL_AUTOLINK,
		MAYBE_EMAIL_AUTOLINK_OR_CLOSING_TAG,
		MAYBE_EMAIL_AUTOLINK_OR_TAGS_CDATA_DECLARATION_COMMENT,
		MAYBE_EMAIL_AUTOLINK_OR_PROCESSING_INSTRUCTION_TAG,
		MAYBE_AUTOLINK_OR_OPEN_TAG,
		MAYBE_EMAIL_AUTOLINK_AFTER_AT_OR_DOT,
		MAYBE_CLOSING_TAG,
		MAYBE_CDATA_TAG_LEAD,
		MAYBE_EMAIL_AUTOLINK_OR_COMMENT_TAG,
		MAYBE_EMAIL_AUTOLINK_OR_DECLARATION_TAG,
		MAYBE_CDATA_TAG_BODY,
		MAYBE_CDATA_TAG_TRAIL,
		MAYBE_OPEN_TAG,
		MAYBE_TAG_ATTRIBUTES_OR_END_OPEN_TAG,
		MAYBE_TAG_ATTRIBUTES,
		MAYBE_ATTRIBUTE_VALUE,
		MAYBE_LINK_REFERENHCE,
		MAYBE_LINK_TITLE,
		MAYBE_LINK_LABEL,
		RECOVERY,
		FINISH,
		MAYBE_LINK_REFERENCE_COLON
	}
	
	private static class InlineParserContext {
		public State state;
		public boolean notEmailAutolink;
		public int pos;
		public char currentChar;
		public StringBuilder sb = new StringBuilder();
		public boolean unescape;
		public boolean processEntity;
	}
	
	private static String unicodeTrim(String str) {
		int length = str.length();
		if (length == 0) return str;
		int beginPos = 0;
		for (int i = 1; i <= length; i++) {
			int cp = str.codePointAt(i-1);
			if (!Character.isWhitespace(cp) &&
				cp != 0xA0 && cp != 0x2007 && cp != 0x202F) break;
			beginPos = i;
		}
		if (beginPos >= length) return "";
		int endPos = length;
		for (int i = length-1; i >= beginPos; i--) {
			endPos = i+1;
			int cp = str.codePointAt(i);
			if (!Character.isWhitespace(cp) &&
				cp != 0xA0 && cp != 0x2007 && cp != 0x202F) break;
		}
		return str.substring(beginPos, endPos);
	}
	
	private static String normalizeWhitespace(String str, Function<Character,Boolean> whitespaseCharFunc) {
		StringBuilder sb = new StringBuilder();
		int length = str.length();
		int beginPos = 0;
		for (int i = 1; i <= length; i++) {
			if (!whitespaseCharFunc.apply(str.charAt(i-1))) break;
			beginPos = i;
		}
		if (beginPos >= length) return "";
		int endPos = length;
		for (int i = length-1; i >= beginPos; i--) {
			endPos = i+1;
			if (!whitespaseCharFunc.apply(str.charAt(i))) break;
		}
		boolean findSpace = false;
		char c;
		for (int i = beginPos; i < endPos; i++) {
			c = str.charAt(i);
			if (whitespaseCharFunc.apply(c)) {
				if (findSpace) continue;
				findSpace = true;
			} else {
				findSpace = false;
			}
			sb.append((c == '\n') ? ' ' : str.charAt(i));
		}
		return sb.toString();
	}
	
	private static String normalizeReference(String reflabel, Function<Character,Boolean> whitespaseCharFunc) {
		StringBuilder sb = new StringBuilder();
		reflabel = normalizeWhitespace(reflabel, whitespaseCharFunc);
		for (int i=0; i < reflabel.length(); i++) {
			sb.appendCodePoint(Character.toUpperCase(reflabel.codePointAt(i)));
		}
		return sb.toString();
	}
	
	private State state;
	private String inputString;
	private int length;
	private CharReader reader;
	private char c;
	private char currentChar;
	private boolean currentEscapedChar;
	private int pos;
	private StringBuilder text = new StringBuilder();
	private int recoveryTextPos;
	private char previousChar;
	private boolean previousEscapedChar;
	private char recoveryPreviousChar;
	private boolean recoveryPreviousEscapedChar;
	private StringBuilder sb = new StringBuilder();
	private Node current;
	private int leftDelimiterRunLength;
	private int rightDelimiterRunLength;
	private boolean isFirstCharAfterAtOrDot;
	private int strLengthAfterAtOrDot;
	private boolean isFirstCharAfterSlash;
	private int charIndexAfterExcl;
	private int cdataLeadIndex;
	private int cdataTrailIndex;
	private boolean twoDash;
	private boolean notEmailAutolink;
	private boolean notCommentTag;
	private boolean declWhitespace;
	private boolean notDeclarationTag;
	private Deque<InlineParserContext> stack;
	private int charIndexAfterQstn;
	private boolean openTagWhitespace;
	private boolean autolinkColon;
	private int autolinkLength;
	private boolean notAutolink;
	private boolean notOpenTag;
	private int tagBeginTextPos;
	private boolean isFirstOpenTagChar;
	private boolean isFirstTagAttributeChar;
	private boolean isAttributeValueBegin;
	private boolean notUnquotedAttributeValue;
	private char attributeValueQuote;
	private boolean isAttrubuteValueEnd;
	private boolean notQuotedAttributeValue;
	private boolean processPreviousChar;
	private Node opener;
	private boolean isLinkReferenceBegin;
	private boolean possiblyLinkReferenceInBraces;
	private boolean notLinkReferenceInBraces;
	private boolean notLinkReference;
	private boolean possiblyLinkReferenceInBracesEnd;
	private boolean inParenthesis;
	private String linkReference;
	private boolean isLinkTitleBegin;
	private char linkTitleMark;
	private boolean isLinkTitleEnd;
	private int linkLabelLength;
	private boolean linkLabelNWChar;
	private boolean finalSpaces;
	private boolean lineBreak;
	private boolean softBreak;
	private boolean removeLeadingSpaces;
	private int referenceDefEndPos;
	private String reflabel;
	private boolean lineEnding;
	private int beforeTitlePos;
	private boolean notWsCharAfterTitle;
	private boolean hasReferenceDefs;
	private StringBuilder tmp = new StringBuilder();
	
	private Function<Character,Boolean> whitespaseCharFunc;
	private Map<String,Link> linkReferenceMap;

	private Map<State,InlineParserHandler> handlersMap;
	private Map<State,InlineParserHandler> rHandlersMap;
	private Map<State,InlineParserHandler> otHandlersMap;

	public InlineParserImpl(CharReader reader) {
		whitespaseCharFunc = DEFAULT_WHITESPACE_CHAR_FUNC;
		this.reader = reader;
		linkReferenceMap = new HashMap<>();
		handlersMap = new HashMap<>();
		handlersMap.put(UNKNOWN_CHAR, this::unknownCharActionHandler);
		handlersMap.put(MAYBE_LEFT_BACKTICK_STRING, this::maybeLeftBacktickStringActionHandler);
		handlersMap.put(MAYBE_CODE_SPAN_BODY, this::maybeCodeSpanBodyActionHandler);
		handlersMap.put(MAYBE_RIGHT_BACKTICK_STRING, this::maybeRightBacktickStringActionHandler);
		handlersMap.put(MAYBE_AUTOLINK_OR_HTML_TAG, this::maybeAutolinkOrHtmlTagActionHandler);
		handlersMap.put(MAYBE_EMAIL_AUTOLINK, this::maybeEmailAutolinkActionHandler);
		handlersMap.put(MAYBE_EMAIL_AUTOLINK_AFTER_AT_OR_DOT, this::maybeEmailAutolinkAfterAtOrDotActionHandler);
		handlersMap.put(MAYBE_EMAIL_AUTOLINK_OR_CLOSING_TAG, this::maybeEmailAutolinkOrClosingTagActionHandler);
		handlersMap.put(MAYBE_CLOSING_TAG, this::maybeClosingTagActionHandler);
		handlersMap.put(MAYBE_EMAIL_AUTOLINK_OR_TAGS_CDATA_DECLARATION_COMMENT, this::maybeEmailAutolinkOrTagsCdataDeclarationCommentActionHandler);
		handlersMap.put(MAYBE_CDATA_TAG_LEAD, this::maybeCdataTagLeadActionHandler);
		handlersMap.put(MAYBE_CDATA_TAG_BODY, this::maybeCdataTagBodyActionHandler);
		handlersMap.put(MAYBE_CDATA_TAG_TRAIL, this::maybeCdataTagTrailActionHandler);
		handlersMap.put(MAYBE_EMAIL_AUTOLINK_OR_COMMENT_TAG, this::maybeEmailAutolinkOrCommentTagActionHandler);
		handlersMap.put(MAYBE_EMAIL_AUTOLINK_OR_DECLARATION_TAG, this::maybeEmailAutolinkOrDeclarationTagActionHandler);
		handlersMap.put(MAYBE_EMAIL_AUTOLINK_OR_PROCESSING_INSTRUCTION_TAG, this::maybeEmailAutolinkOrProcessingInstructionTagActionHandler);
		handlersMap.put(MAYBE_AUTOLINK_OR_OPEN_TAG, this::maybeAutolinkOrOpenTagActionHandler);
		handlersMap.put(MAYBE_OPEN_TAG, this::maybeOpenTagActionHandler);
		handlersMap.put(MAYBE_TAG_ATTRIBUTES_OR_END_OPEN_TAG, this::maybeTagAttributesOrEndOpenTagActionHandler);
		handlersMap.put(MAYBE_TAG_ATTRIBUTES, this::maybeTagAttributesActionHandler);
		handlersMap.put(MAYBE_ATTRIBUTE_VALUE, this::maybeAttributeValueActionHandler);
		handlersMap.put(MAYBE_LINK_REFERENHCE, this::maybeLinkReferenceActionHandler);
		handlersMap.put(MAYBE_LINK_TITLE, this::maybeLinkTitleActionHandler);
		handlersMap.put(MAYBE_LINK_LABEL, this::maybeLinkLabelActionHandler);
		rHandlersMap = new HashMap<>();
		rHandlersMap.put(UNKNOWN_CHAR, this::rUnknownCharActionHandler);
		rHandlersMap.put(MAYBE_LINK_LABEL, this::rMaybeLinkLabelActionHandler);
		rHandlersMap.put(MAYBE_LINK_REFERENCE_COLON, this::rMaybeLinkReferenceColonActionHandler);
		rHandlersMap.put(MAYBE_LINK_REFERENHCE, this::rMaybeLinkReferenceActionHandler);
		rHandlersMap.put(MAYBE_LINK_TITLE, this::rMaybeLinkTitleActionHandler);
		otHandlersMap = new HashMap<>();
		otHandlersMap.put(UNKNOWN_CHAR, this::otUnknownCharActionHandler);
		otHandlersMap.put(MAYBE_OPEN_TAG, this::maybeOpenTagActionHandler);
		otHandlersMap.put(MAYBE_TAG_ATTRIBUTES_OR_END_OPEN_TAG, this::otMaybeTagAttributesOrEndOpenTagActionHandler);
		otHandlersMap.put(MAYBE_TAG_ATTRIBUTES, this::maybeTagAttributesActionHandler);
		otHandlersMap.put(MAYBE_ATTRIBUTE_VALUE, this::maybeAttributeValueActionHandler);
	}
	
	@Override
	public void whitespaseCharFunc(Function<Character,Boolean> func) {
		whitespaseCharFunc = func;
	}
	
	@Override
	public Map<String,Link> refmap() {
		return linkReferenceMap;
	}
	
	@Override
	public void recreateStringBuilders() {
		text = new StringBuilder();
		sb = new StringBuilder();
		tmp = new StringBuilder();
	}
	
	@Override
	public boolean isWhitespase(char c) {
		return whitespaseCharFunc.apply(c);
	}
	
	private void saveContext() {
		InlineParserContext context =
			new InlineParserContext();
		context.state = state;
		context.notEmailAutolink = notEmailAutolink;
		context.pos = reader.pos();
		context.currentChar = currentChar;
		context.sb.setLength(0);
		if (sb.length() > 0) {
			context.sb.append(sb);
			context.sb.append(c);
		}
		context.unescape = reader.isUnescape();
		context.processEntity = reader.isProcessEntity();
		stack.addFirst(context);
	}
	
	private void restoreContext() {
		InlineParserContext context =
			stack.removeFirst();
		state = context.state;
		notEmailAutolink = context.notEmailAutolink;
		currentChar = context.currentChar;
		sb.setLength(0);
		if (context.sb.length() > 0)
			sb.append(context.sb);
		reader.unescape(context.unescape);
		reader.processEntity(context.processEntity);
		reader.pos(context.pos);
	}
	
	@Override
	public String unescapeString(String str) {
		tmp.setLength(0);
		CharReader reader = this.reader;
		reader.inputString(str);
		reader.unescape(true);
		reader.processEntity(true);
		while (reader.hasNext())
			tmp.append(reader.next());
		return tmp.toString();
	}

	private void processPreviousText() {
		if (text.length() > 0) {
			Node last = current.childs().peekLast();
			if (last instanceof InlineTextNode)
				((InlineTextNode) last)
					.literal(((InlineTextNode) last).literal()+text.toString());
			else {
				InlineTextNode t = new InlineTextNode();
				t.literal(text.toString());
				current.childs().addLast(t);
			}
			text.setLength(0);
		}
	}
	
	private Node findOpener() {
		Node current = this.current;
		do {
			if (current instanceof InlineBangNode)
				return current;
			if (current instanceof InlineOpenBracketNode) {
				if (((InlineOpenBracketNode) current).isActive())
					return current;
				else {
					replaceNodeByTextNode(current, "[");
					return null;
				}
			}
			current = current.parent();
		} while (current != null && current instanceof InlineNode);
		return null;
	}
	
	private boolean isSpace(char c) {
		return c == '\t' || c == '\r' ||
			c == '\n' || c == '\f' ||
			Character.getType(c) == Character.SPACE_SEPARATOR;
	}
	
	private boolean isPunctuation(char c) {
		boolean isPunctuation = ASCII_PUNCTUATION.indexOf(c) >= 0;
		if (!isPunctuation) {
			int type = Character.getType(c);
			isPunctuation = type == Character.CONNECTOR_PUNCTUATION ||
				type == Character.DASH_PUNCTUATION ||
				type == Character.END_PUNCTUATION ||
				type == Character.FINAL_QUOTE_PUNCTUATION ||
				type == Character.INITIAL_QUOTE_PUNCTUATION ||
				type == Character.OTHER_PUNCTUATION ||
				type == Character.START_PUNCTUATION;
		}
		return isPunctuation;
	}
	
	private void deactivateInlineOpenBracketNode() {
		Node current = opener.parent();
		while (current != null && current instanceof InlineNode) {
			if (current instanceof InlineOpenBracketNode)
				((InlineOpenBracketNode) current).deactivate();
			current = current.parent();
		}
	}
	
	private void insertLinkOrImageNode(String reference, String title) {
		processPreviousText();
		Node n;
		if (opener instanceof InlineOpenBracketNode) {
			deactivateInlineOpenBracketNode();
			InlineLinkNode l = new InlineLinkNode();
			l.reference(reference);
			l.title(title);
			n = l;
		} else {
			InlineImageNode i = new InlineImageNode();
			i.reference(reference);
			i.title(title);
			n = i;
		}
		Node parent = opener.parent();
		opener.childs().forEach(node -> {
			if (node.parent() != null)
				node.parent(n);
		});
		n.childs().addAll(opener.childs());
		parent.childs().removeLast();
		parent.childs().addLast(n);
		current = parent;
		removeOpenBracketOrBangNodes(n);
		processEmphasis(n);
	}
	
	private void replaceNodeByTextNode(Node node, String text) {
		Node parent = node.parent();
		if (current == node) current = parent;
		parent.childs().removeLast();
		Node last = parent.childs().peekLast();
		Node first = node.childs().peekFirst();
		if (last != null &&
			(last instanceof InlineTextNode))
			if (first != null &&
				(first instanceof InlineTextNode)) {
				((InlineTextNode) last)
				.literal(((InlineTextNode) last)
					.literal()+text+((InlineTextNode) first).literal());
				node.childs().removeFirst();
			} else {
				((InlineTextNode) last)
					.literal(((InlineTextNode) last).literal()+text);
			}
		else if (first != null &&
			(first instanceof InlineTextNode))
			((InlineTextNode) first)
			.literal(text+((InlineTextNode) first).literal());
		else {
			InlineTextNode t = new InlineTextNode();
			t.literal(text);
			parent.childs().addLast(t);
		}
		node.childs().forEach(cNode -> {
			if (cNode.parent() != null)
				cNode.parent(parent);
		});
		parent.childs().addAll(node.childs());
	}
	
	private void removeOpenBracketOrBangNodes(Node current) {
		while (current != null) {
			if (current instanceof InlineOpenBracketNode)
				replaceNodeByTextNode(current, "[");
			else if (current instanceof InlineBangNode)
				replaceNodeByTextNode(current, "![");
			current = current.childs().peekLast();
		}
	}
	
	private void removeEmphasisNode(Node current) {
		while (current != null) {
			if (current instanceof InlineEmphasisDelimiterNode) {
				tmp.setLength(0);
				InlineEmphasisDelimiterNode node =
					(InlineEmphasisDelimiterNode) current;
				char delimiter = node.delimiter();
				for (int i=0; i < node.numdelims(); i++)
					tmp.append(delimiter);
				replaceNodeByTextNode(current, tmp.toString());
			}
			current = current.childs().peekLast();
		}
	}
	
	private void processEmphasis(Node current) {
		Node root = current;
		InlineEmphasisDelimiterNode closer;
		while (current != null) {
			if ((current instanceof InlineEmphasisDelimiterNode) &&
				(closer = (InlineEmphasisDelimiterNode) current).canClose()) {
				char delimiter = closer.delimiter();
				Node potentialOpener = closer.parent();
				InlineEmphasisDelimiterNode opener;
				boolean found = false;
				while (potentialOpener != null && potentialOpener != root) {
					if ((potentialOpener instanceof InlineEmphasisDelimiterNode) &&
						(opener = (InlineEmphasisDelimiterNode) potentialOpener).canOpen() &&
						opener.delimiter() == delimiter) {
						int useDelims;
						if (closer.numdelims() < 3 || opener.numdelims() < 3) {
							useDelims = closer.numdelims() <= opener.numdelims() ?
								closer.numdelims() : opener.numdelims();
						} else {
							useDelims = closer.numdelims() % 2 == 0 ? 2 : 1;
						}
						Node e;
						if (useDelims == 1) {
							e = new InlineEmphasisNode();
							opener.decNumdelims();
							closer.decNumdelims();
						} else {
							e = new InlineStrongEmphasisNode();
							opener.decBy2Numdelims();
							closer.decBy2Numdelims();
						}
						closer.parent().childs().removeLast();
						opener.childs().forEach(node -> {
							if (node.parent() != null)
								node.parent(e);
						});
						e.childs().addAll(opener.childs());
						opener.childs().clear();
						opener.childs().addFirst(e);
						opener.childs().addLast(closer);
						closer.parent(opener);
						removeEmphasisNode(e);
						if (opener.numdelims() == 0) {
							Node parent = opener.parent();
							parent.childs().removeLast();
							opener.childs().forEach(node -> {
								if (node.parent() != null)
									node.parent(parent);
							});
							parent.childs().addAll(opener.childs());
						}
						found = true;
						if (closer.numdelims() == 0) {
							found = false;
							Node parent = closer.parent();
							parent.childs().removeLast();
							closer.childs().forEach(node -> {
								if (node.parent() != null)
									node.parent(parent);
							});
							parent.childs().addAll(closer.childs());
							current = parent;
							if (current == opener && opener.numdelims() == 0)
								current = opener.parent();
						}
						break;
					}
					potentialOpener = potentialOpener.parent();
				}
				if (found) continue;
			}
			current = current.childs().peekLast();
		}
		removeEmphasisNode(root);
	}
	
	private void finalizeEmphasisDelimiterNode(InlineEmphasisDelimiterNode node) {
		char charAfter = (reader.hasNext()) ? c : '\n';
		boolean afterIsSpace;
		boolean afterIsPunctuation = false;
		if (!(afterIsSpace = isSpace(charAfter)))
			afterIsPunctuation = isPunctuation(charAfter);
		boolean canOpen = !afterIsSpace && !(afterIsPunctuation &&
			!node.beforeIsSpace() && !node.beforeIsPunctuation());
		boolean canClose = !node.beforeIsSpace() && !(node.beforeIsPunctuation() &&
			!afterIsSpace && !afterIsPunctuation);
		if (node.delimiter() == '_') {
			boolean temp = canOpen;
			canOpen &= (!canClose || node.beforeIsPunctuation());
			canClose &= (!temp || afterIsPunctuation);
		}
		node.canOpen(canOpen);
		node.canClose(canClose);
		node.deactivate();
	}
	
	private void unknownCharActionHandler() {
		boolean previousBang = previousChar == '!' && !previousEscapedChar;
		boolean previousBackslash = previousChar == '\\' && !previousEscapedChar;
		if (previousBang && !(c == '[' && !currentEscapedChar))
			text.append('!');
		if (previousBackslash && c != '\n')
			text.append('\\');
		if ((lineBreak || softBreak || removeLeadingSpaces) && c != ' ') {
			if (removeLeadingSpaces) {
				removeLeadingSpaces = false;
			} else {
				processPreviousText();
				Node n;
				if (lineBreak) {
					n = new InlineLineBreakNode();
					lineBreak = false;
				} else {
					n = new InlineSoftBreakNode();
					softBreak = false;
				}
				current.childs().addLast(n);
				finalSpaces = false;
			}
			sb.setLength(0);
		} else if (finalSpaces && c != '\n' && c != ' ') {
			text.append(sb);
			finalSpaces = false;
			sb.setLength(0);
		}
		InlineEmphasisDelimiterNode en;
		if (!((c == '*' || c == '_') && !currentEscapedChar) &&
			(current instanceof InlineEmphasisDelimiterNode) &&
			(en = (InlineEmphasisDelimiterNode) current).isActive()) {
			finalizeEmphasisDelimiterNode(en);
		}
		boolean previousCloseBracket = processPreviousChar &&
			previousChar == ']' && !previousEscapedChar;
		boolean closeBracket = c == ']' && !currentEscapedChar;
		boolean bang = c == '!' && !currentEscapedChar && reader.hasNext();
		boolean backslash = c == '\\' && !currentEscapedChar && reader.hasNext();
		if (((previousCloseBracket || closeBracket)
			&& !reader.hasNext()) ||
			(!(!currentEscapedChar && (c == '[' || c == '(')) &&
			previousCloseBracket)) {
			if ((opener = findOpener()) != null) {
				int begin;
				if (opener instanceof InlineOpenBracketNode)
					begin = ((InlineOpenBracketNode) opener).index();
				else
					begin = ((InlineBangNode) opener).index();
				int end;
				if (previousCloseBracket)
					end = pos-1;
				else
					end = pos;
				String rawreflabel = inputString.substring(begin, end);
				String reflabel = normalizeReference(rawreflabel, whitespaseCharFunc);
				Link link;
				if (!reflabel.isEmpty() && (link = linkReferenceMap.get(reflabel)) != null) {
					insertLinkOrImageNode(link.reference, link.title);
				} else {
					text.append(']');
					if (opener instanceof InlineOpenBracketNode)
						replaceNodeByTextNode(opener, "[");
					else
						replaceNodeByTextNode(opener, "![");
				}
				if (closeBracket &&
					previousCloseBracket &&
					!reader.hasNext()) {
					processPreviousChar = false;
					reader.repeat();
					return;
				}
				processPreviousChar = false;
			} else {
				text.append(']');
				processPreviousChar = false;
			}
		}
		if (c == '`' && !currentEscapedChar &&
			!(previousChar == '`' && !previousEscapedChar)) {
			state = MAYBE_LEFT_BACKTICK_STRING;
			reader.unescape(false);
			reader.processEntity(false);
			leftDelimiterRunLength = 1;
			sb.setLength(0);
			sb.append(c);
		} else if (c == '<' && !currentEscapedChar) {
			state = MAYBE_AUTOLINK_OR_HTML_TAG;
			reader.unescape(false);
			sb.setLength(0);
			sb.append(c);
		} else if (c == '[' && !currentEscapedChar &&
			previousCloseBracket && reader.hasNext()
			&& (opener = findOpener()) != null) {
			state = MAYBE_LINK_LABEL;
			sb.setLength(0);
			linkLabelLength = 0;
			linkLabelNWChar = false;
			reader.processEntity(false);
		} else if (c == '(' && !currentEscapedChar &&
			previousCloseBracket && reader.hasNext()
			&& (opener = findOpener()) != null) {
			state = MAYBE_LINK_REFERENHCE;
			sb.setLength(0);
			isLinkReferenceBegin = false;
			possiblyLinkReferenceInBraces = false;
			notLinkReferenceInBraces = false;
			notLinkReference = false;
			possiblyLinkReferenceInBracesEnd = false;
			inParenthesis = false;
		} else if (c == '['	&& !currentEscapedChar) {
			if (processPreviousChar)
				text.append(previousChar);
			processPreviousText();
			if (previousBang) {
				InlineBangNode n = new InlineBangNode(current);
				n.index(reader.pos());
				current.childs().addLast(n);
				current = n;
			} else {
				InlineOpenBracketNode n = new InlineOpenBracketNode(current);
				n.index(reader.pos());
				current.childs().addLast(n);
				current = n;
			}
		} else if ((c == '*' || c == '_') && !currentEscapedChar) {
			InlineEmphasisDelimiterNode e;
			if ((current instanceof InlineEmphasisDelimiterNode) &&
				(e = (InlineEmphasisDelimiterNode) current).delimiter() == c &&
				e.isActive()) {
				e.incNumdelims();
			} else {
				if ((current instanceof InlineEmphasisDelimiterNode) &&
					(e = (InlineEmphasisDelimiterNode) current).isActive()) {
					boolean canOpen = !(!e.beforeIsSpace() && !e.beforeIsPunctuation());
					boolean canClose = !e.beforeIsSpace();
					if (e.delimiter() == '_')
						canOpen &= (!canClose || e.beforeIsPunctuation());
					e.canOpen(canOpen);
					e.canClose(canClose);
					e.deactivate();
				}
				processPreviousText();
				char prevChar = (pos == 0) ? '\n' : previousChar;
				boolean beforeIsSpace;
				boolean beforeIsPunctuation = false;
				if (!(beforeIsSpace = isSpace(prevChar)))
					beforeIsPunctuation = isPunctuation(prevChar);
				e = new InlineEmphasisDelimiterNode(current, c, beforeIsSpace, beforeIsPunctuation);
				current.childs().addLast(e);
				current = e;
			}
			if (!reader.hasNext())
				finalizeEmphasisDelimiterNode(e);
		} else if (c == ' ' && !lineBreak && !softBreak & !removeLeadingSpaces) {
			if (!finalSpaces) {
				sb.setLength(0);
				finalSpaces = true;
				lineBreak = false;
				softBreak = false;
			}
			sb.append(c);
		} else if (c == '\n') {
			if (previousBackslash) {
				processPreviousText();
				Node n = new InlineLineBreakNode();
				current.childs().addLast(n);
				removeLeadingSpaces = true;
			} else if (finalSpaces && sb.length() > 1) {
				lineBreak = true;
			} else {
				softBreak = true;
			}
		} else {
			if (processPreviousChar)
				text.append(previousChar);
			if (!bang && !closeBracket && !backslash &&
				!finalSpaces && !lineBreak && !softBreak && !removeLeadingSpaces)
				text.append(c);
		}
		recoveryTextPos = reader.pos();
		recoveryPreviousChar = c;
		recoveryPreviousEscapedChar = currentEscapedChar;
		processPreviousChar = closeBracket;
		if (!stack.isEmpty()) stack.clear();
	}
	
	private void maybeLeftBacktickStringActionHandler() {
		if (c == '`') {
			leftDelimiterRunLength++;
		} else {
			state = MAYBE_CODE_SPAN_BODY;
		}
		sb.append(c);
	}
	
	private void maybeCodeSpanBodyActionHandler() {
		if (c == '`') {
			state = MAYBE_RIGHT_BACKTICK_STRING;
			reader.repeat();
			currentChar = previousChar;
			currentEscapedChar = previousEscapedChar;
			rightDelimiterRunLength = 0;
			return;
		}
		sb.append(c);
	}

	private void maybeRightBacktickStringActionHandler() {
		if (c == '`' && reader.hasNext()) {
			rightDelimiterRunLength++;
		} else if (rightDelimiterRunLength == leftDelimiterRunLength ||
			(c == '`' && !reader.hasNext() && rightDelimiterRunLength+1 == leftDelimiterRunLength)) {
			processPreviousText();
			InlineCodeNode cb = new InlineCodeNode();
			cb.literal(normalizeWhitespace(
				sb.substring(leftDelimiterRunLength, sb.length()-rightDelimiterRunLength),
				whitespaseCharFunc));
			current.childs().addLast(cb);
			if (rightDelimiterRunLength == leftDelimiterRunLength) {
				reader.repeat();
				currentChar = previousChar;
				currentEscapedChar = previousEscapedChar;
			}
			state = UNKNOWN_CHAR;
			reader.unescape(true);
			reader.processEntity(true);
			return;
		} else {
			state = MAYBE_CODE_SPAN_BODY;
		}
		sb.append(c);
	}
	
	private void maybeAutolinkOrHtmlTagActionHandler() {
		if ((c >= '0' && c <= '9') ||
			".#$%&'*+=^_`{|}~-".indexOf(c) >= 0) {
			state = MAYBE_EMAIL_AUTOLINK;
		} else if (c == '/') {
			state = MAYBE_EMAIL_AUTOLINK_OR_CLOSING_TAG;
			isFirstCharAfterSlash = true;
		} else if (c == '!') {
			state = MAYBE_EMAIL_AUTOLINK_OR_TAGS_CDATA_DECLARATION_COMMENT;
			charIndexAfterExcl = 0;
		} else if (c == '?') {
			state = MAYBE_EMAIL_AUTOLINK_OR_PROCESSING_INSTRUCTION_TAG;
			charIndexAfterQstn = 0;
			notEmailAutolink = false;
		} else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
			state = MAYBE_AUTOLINK_OR_OPEN_TAG;
			notEmailAutolink = false;
			autolinkColon = false;
			openTagWhitespace = false;
			autolinkLength = 0;
			notAutolink = false;
			notOpenTag = false;
			tagBeginTextPos = pos;
		} else {
			state = RECOVERY;
			return;
		}
		sb.append(c);
	}
	
	private void maybeEmailAutolinkActionHandler() {
		if (c == '@') {
			state = MAYBE_EMAIL_AUTOLINK_AFTER_AT_OR_DOT;
			isFirstCharAfterAtOrDot = true;
			strLengthAfterAtOrDot = 0;
		} else if (!((c >= '0' && c <= '9') ||
			(c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
			".!#$%&'*+/=?^_`{|}~-".indexOf(c) >= 0)) {
			state = RECOVERY;
			return;
		}
		sb.append(c);
	}
	
	private void maybeEmailAutolinkAfterAtOrDotActionHandler() {
		if ((c == '.' || c == '>')
			&& previousChar != '-'
			&& !isFirstCharAfterAtOrDot &&
			strLengthAfterAtOrDot <= 63) {
			if (c == '.') {
				isFirstCharAfterAtOrDot = true;
				strLengthAfterAtOrDot = 0;
				sb.append(c);
				return;
			} else {
				processPreviousText();
				InlineLinkNode l = new InlineLinkNode();
				String s = sb.substring(1);
				String reference = "mailto:" + s;
				l.reference(reference);
				l.title("");
				current.childs().addLast(l);
				InlineTextNode t = new InlineTextNode();
				t.literal(s);
				l.childs().addLast(t);
				state = UNKNOWN_CHAR;
				reader.unescape(true);
				return;
			}
		} else if (!((c >= '0' && c <= '9') ||
			(c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
			(c == '-' && !isFirstCharAfterAtOrDot)) ||
			strLengthAfterAtOrDot >= 63) {
			state = RECOVERY;
			return;
		}
		isFirstCharAfterAtOrDot = false;
		strLengthAfterAtOrDot++;
		sb.append(c);
	}
	
	private void maybeEmailAutolinkOrClosingTagActionHandler() {
		if (c == '@') {
			state = MAYBE_EMAIL_AUTOLINK_AFTER_AT_OR_DOT;
			isFirstCharAfterAtOrDot = true;
			strLengthAfterAtOrDot = 0;
		} else if ((isFirstCharAfterSlash && (c == '-' ||
			(c >= '0' && c <= '9'))) ||
			".!#$%&'*+/=?^_`{|}~".indexOf(c) >= 0) {
			state = MAYBE_EMAIL_AUTOLINK;
		} else if (whitespaseCharFunc.apply(c)) {
			state = MAYBE_CLOSING_TAG;
		} else if (c == '>') {
			state = MAYBE_CLOSING_TAG;
			reader.repeat();
			currentChar = previousChar;
			currentEscapedChar = previousEscapedChar;
			return;
		} else if (!((c >= '0' && c <= '9') ||
			(c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
			".!#$%&'*+/=?^_`{|}~-".indexOf(c) >= 0)) {
			state = RECOVERY;
			return;
		}
		isFirstCharAfterSlash = false;
		sb.append(c);
	}
	
	private void maybeClosingTagActionHandler() {
		if (c == '>') {
			processPreviousText();
			InlineHtmlNode h = new InlineHtmlNode();
			sb.append(c);
			h.literal(sb.toString());
			current.childs().addLast(h);
			state = UNKNOWN_CHAR;
			reader.unescape(true);
		} else if (!whitespaseCharFunc.apply(c)) {
			state = RECOVERY;
		} else {
			sb.append(c);
		}
	}
	
	private void maybeEmailAutolinkOrTagsCdataDeclarationCommentActionHandler() {
		if (c == '@') {
			state = MAYBE_EMAIL_AUTOLINK_AFTER_AT_OR_DOT;
			isFirstCharAfterAtOrDot = true;
			strLengthAfterAtOrDot = 0;
		} else if (c == '[') {
			state = MAYBE_CDATA_TAG_LEAD;
			cdataLeadIndex = 0;
			reader.processEntity(false);
		} else if ((c >= '0' && c <= '9') ||
			(c >= 'a' && c <= 'z') ||
			".!#$%&'*+/=?^_`{|}~".indexOf(c) >= 0) {
			state = MAYBE_EMAIL_AUTOLINK;
		} else if (c == '-') {
			state = MAYBE_EMAIL_AUTOLINK_OR_COMMENT_TAG;
			notEmailAutolink = false;
			notCommentTag = false;
		} else if (c >= 'A' && c <= 'Z') {
			state = MAYBE_EMAIL_AUTOLINK_OR_DECLARATION_TAG;
			notEmailAutolink = false;
			notDeclarationTag = false;
			declWhitespace = false;
		} else {
			state = RECOVERY;
			return;
		}
		charIndexAfterExcl++;
		sb.append(c);
	}
	
	private void maybeCdataTagLeadActionHandler() {
		if (c != CDATA_LEAD.charAt(cdataLeadIndex)) {
			state = RECOVERY;
			return;
		}
		cdataLeadIndex++;
		if (cdataLeadIndex == 6)
			state = MAYBE_CDATA_TAG_BODY;
		sb.append(c);
	}
	
	private void maybeCdataTagBodyActionHandler() {
		if (c == ']') {
			state = MAYBE_CDATA_TAG_TRAIL;
			cdataTrailIndex = 1;
		}
		sb.append(c);
	}
	
	private void maybeCdataTagTrailActionHandler() {
		if (c != CDATA_TRAIL.charAt(cdataTrailIndex))
			state = MAYBE_CDATA_TAG_BODY;
		cdataTrailIndex++;
		if (cdataTrailIndex == 3) {
			processPreviousText();
			InlineHtmlNode h = new InlineHtmlNode();
			sb.append(c);
			h.literal(sb.toString());
			current.childs().addLast(h);
			state = UNKNOWN_CHAR;
			reader.unescape(true);
			reader.processEntity(true);
			return;
		}
		sb.append(c);
	}
	
	private void maybeEmailAutolinkOrCommentTagActionHandler() {
		if (!((c >= '0' && c <= '9') ||
			(c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
			"@.!#$%&'*+/=?^_`{|}~-".indexOf(c) >= 0))
			notEmailAutolink = true;
		if ((c != '-' && charIndexAfterExcl == 1) ||
			(c != '>' && twoDash && charIndexAfterExcl > 3) ||
			(c == '>' && charIndexAfterExcl == 2) ||
			(c == '>' && charIndexAfterExcl == 3 && previousChar == '-'))
			notCommentTag = true;
		if (notCommentTag &&
			!notEmailAutolink && c != '@') {
			state = MAYBE_EMAIL_AUTOLINK;
		} else if (c == '@' && !notEmailAutolink && notCommentTag) {
			state = MAYBE_EMAIL_AUTOLINK_AFTER_AT_OR_DOT;
			isFirstCharAfterAtOrDot = true;
			strLengthAfterAtOrDot = 0;
		} else if (c == '@' && !notEmailAutolink && !notCommentTag) {
			notEmailAutolink = true;
			saveContext();
			state = MAYBE_EMAIL_AUTOLINK_AFTER_AT_OR_DOT;
			isFirstCharAfterAtOrDot = true;
			strLengthAfterAtOrDot = 0;
		} else if (c == '>' && twoDash && charIndexAfterExcl > 3) {
			processPreviousText();
			InlineHtmlNode h = new InlineHtmlNode();
			sb.append(c);
			h.literal(sb.toString());
			current.childs().addLast(h);
			state = UNKNOWN_CHAR;
			reader.unescape(true);
			return;
		} else if (notEmailAutolink && notCommentTag) {
			state = RECOVERY;
			return;
		}
		sb.append(c);
		if (charIndexAfterExcl > 1 &&
			(c == '-' && previousChar == '-'))
			twoDash = true;
		else
			twoDash = false;
		charIndexAfterExcl++;
	}
	
	private void maybeEmailAutolinkOrDeclarationTagActionHandler() {
		if (!((c >= '0' && c <= '9') ||
			(c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
			"@.!#$%&'*+/=?^_`{|}~-".indexOf(c) >= 0))
			notEmailAutolink = true;
		if (whitespaseCharFunc.apply(c))
			declWhitespace = true;
		if (!(c >= 'A' && c <= 'Z') && !declWhitespace)
			notDeclarationTag = true;
		if (notDeclarationTag &&
			!notEmailAutolink && c != '@') {
			state = MAYBE_EMAIL_AUTOLINK;
		} else if (c == '@' && !notEmailAutolink && notDeclarationTag) {
			state = MAYBE_EMAIL_AUTOLINK_AFTER_AT_OR_DOT;
			isFirstCharAfterAtOrDot = true;
			strLengthAfterAtOrDot = 0;
		} else if (c == '>' && declWhitespace && !notDeclarationTag) {
			processPreviousText();
			InlineHtmlNode h = new InlineHtmlNode();
			sb.append(c);
			h.literal(sb.toString());
			current.childs().addLast(h);
			state = UNKNOWN_CHAR;
			reader.unescape(true);
			return;
		} else if (notDeclarationTag && notEmailAutolink) {
			state = RECOVERY;
			return;
		}
		sb.append(c);
	}
	
	private void maybeEmailAutolinkOrProcessingInstructionTagActionHandler() {
		if (!((c >= '0' && c <= '9') ||
			(c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
			"@.!#$%&'*+/=?^_`{|}~-".indexOf(c) >= 0))
			notEmailAutolink = true;
		if (c == '@' && !notEmailAutolink) {
			notEmailAutolink = true;
			saveContext();
			state = MAYBE_EMAIL_AUTOLINK_AFTER_AT_OR_DOT;
			isFirstCharAfterAtOrDot = true;
			strLengthAfterAtOrDot = 0;
		} else if (c == '>' && previousChar == '?' && charIndexAfterQstn != 0) {
			processPreviousText();
			InlineHtmlNode h = new InlineHtmlNode();
			sb.append(c);
			h.literal(sb.toString());
			current.childs().addLast(h);
			state = UNKNOWN_CHAR;
			reader.unescape(true);
			return;
		}
		sb.append(c);
		charIndexAfterQstn++;
	}
	
	private void maybeAutolinkOrOpenTagActionHandler() {
		if (!((c >= '0' && c <= '9') ||
			(c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
			"@.!#$%&'*+/=?^_`{|}~-".indexOf(c) >= 0))
			notEmailAutolink = true;
		if (c == ':')
			autolinkColon = true;
		if (whitespaseCharFunc.apply(c))
			openTagWhitespace = true;
		if ((c == ':' && autolinkLength == 0) ||
			(!autolinkColon &&
			(!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') ||
			(c >= 'A' && c <= 'Z') || c == '.' || c == '+' || c == '-') ||
			autolinkLength > 31)) ||
			(autolinkColon && ((c >= 0 && c <= 0x20) ||
			c == '<' || previousChar == '>'))) {
			notAutolink = true;
		}
		if (!openTagWhitespace && ((c != '/' && c != '>' &&
			!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') ||
			(c >= 'A' && c <= 'Z') || c == '-')) ||
			previousChar == '/' && c != '>')) {
			notOpenTag = true;
		}
		if (notOpenTag && notAutolink &&
			!notEmailAutolink && c != '@') {
			state = MAYBE_EMAIL_AUTOLINK;
		} else if (c == '@' && !notEmailAutolink) {
			state = MAYBE_EMAIL_AUTOLINK_AFTER_AT_OR_DOT;
			isFirstCharAfterAtOrDot = true;
			strLengthAfterAtOrDot = 0;
		} else if (c == '>' && autolinkColon && !notAutolink) {
			processPreviousText();
			InlineLinkNode l = new InlineLinkNode();
			String s = sb.substring(1);
			String reference = percentEncode(percentDecode(s));
			l.reference(reference);
			l.title("");
			current.childs().addLast(l);
			InlineTextNode t = new InlineTextNode();
			t.literal(s);
			l.childs().addLast(t);
			state = UNKNOWN_CHAR;
			reader.unescape(true);
			return;
		} else if (((notAutolink && notEmailAutolink) ||
			c == '>') &&
			!notOpenTag) {
			state = MAYBE_OPEN_TAG;
			sb.setLength(0);
			sb.append('<');
			isFirstOpenTagChar = true;
			reader.pos(tagBeginTextPos);
			reader.processEntity(false);
			currentChar = '<';
			return;
		} else if (notAutolink && notEmailAutolink &&
			notOpenTag) {
			state = RECOVERY;
			return;
		}
		sb.append(c);
		autolinkLength++;
	}
	
	private void maybeOpenTagActionHandler() {
		if (!((c >= 'a' && c <= 'z') ||
			(c >= 'A' && c <= 'Z') || (!isFirstOpenTagChar &&
			((c >= '0' && c <= '9') || c == '-' || c == '/' || c == '>' ||
			whitespaseCharFunc.apply(c))))) {
			state = RECOVERY;
		} else if (whitespaseCharFunc.apply(c) ||  c == '/' || c == '>') {
			state = MAYBE_TAG_ATTRIBUTES_OR_END_OPEN_TAG;
			reader.repeat();
			currentChar = previousChar;
			currentEscapedChar = previousEscapedChar;
		} else {
			isFirstOpenTagChar = false;;
			sb.append(c);
		}
	}
	
	private void maybeTagAttributesOrEndOpenTagActionHandler() {
		if (c == '>') {
			processPreviousText();
			InlineHtmlNode h = new InlineHtmlNode();
			sb.append(c);
			h.literal(sb.toString());
			current.childs().addLast(h);
			state = UNKNOWN_CHAR;
			reader.unescape(true);
			reader.processEntity(true);
			return;
		} else if ((whitespaseCharFunc.apply(c) || c == '/') &&
			previousChar != '/' ) {
			sb.append(c);
		} else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
			c == '_' || c == ':') {
			state = MAYBE_TAG_ATTRIBUTES;
			reader.repeat();
			currentChar = previousChar;
			currentEscapedChar = previousEscapedChar;
			isFirstTagAttributeChar = true;
			openTagWhitespace = false;
		} else {
			state = RECOVERY;
		}
	}
	
	private void maybeTagAttributesActionHandler() {
		if (whitespaseCharFunc.apply(c))
			openTagWhitespace = true;
		if ((!openTagWhitespace && ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
			c == '_' || c == ':' || (!isFirstTagAttributeChar &&
			((c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-')))) ||
			openTagWhitespace && whitespaseCharFunc.apply(c)) {
			sb.append(c);
			isFirstTagAttributeChar = false;
		} else if (c == '=' && !isFirstTagAttributeChar) {
			sb.append(c);
			state = MAYBE_ATTRIBUTE_VALUE;
			isAttributeValueBegin = false;
			notUnquotedAttributeValue = false;
			attributeValueQuote = 0;
			isAttrubuteValueEnd = false;
			notQuotedAttributeValue = false;
		} else {
			state = MAYBE_TAG_ATTRIBUTES_OR_END_OPEN_TAG;
			reader.repeat();
			currentChar = previousChar;
			currentEscapedChar = previousEscapedChar;
		}
	}
	
	private void maybeAttributeValueActionHandler() {
		if (!whitespaseCharFunc.apply(c) && c != '>')
			isAttributeValueBegin = true;
		if (isAttributeValueBegin) {
			if (c == '"' || c == '\'' || c == '=' || c == '<' || c == '`')
				notUnquotedAttributeValue = true;
			if (!notQuotedAttributeValue && isAttrubuteValueEnd &&
				!(c == '>' || c == '/' || whitespaseCharFunc.apply(c)))
				notQuotedAttributeValue = true;
			if (!notQuotedAttributeValue && attributeValueQuote == c)
				isAttrubuteValueEnd = true;
			if ((c == '\'' || c == '"') && attributeValueQuote == 0)
				attributeValueQuote = c;
			if (attributeValueQuote == 0)
				notQuotedAttributeValue = true;
		}
		if (isAttributeValueBegin &&
			notUnquotedAttributeValue &&
			notQuotedAttributeValue) {
			state = RECOVERY;
		} else if (isAttributeValueBegin &&
			((c == '>' || whitespaseCharFunc.apply(c)) &&
			(!notUnquotedAttributeValue || isAttrubuteValueEnd) ||
			c == '/' && isAttrubuteValueEnd)) {
			state = MAYBE_TAG_ATTRIBUTES_OR_END_OPEN_TAG;
			reader.repeat();
			currentChar = previousChar;
			currentEscapedChar = previousEscapedChar;
		} else {
			sb.append(c);
		}
	}
	
	private void maybeLinkReferenceActionHandler() {
		boolean prevInParenthesis = inParenthesis;
		boolean prevIsLinkDestinationBegin = isLinkReferenceBegin;
		if (!whitespaseCharFunc.apply(c) && c != ')')
			isLinkReferenceBegin = true;
		if (isLinkReferenceBegin) {
			if ((!prevIsLinkDestinationBegin &&
				isLinkReferenceBegin &&
				!(c == '<' && !currentEscapedChar)) ||
				(possiblyLinkReferenceInBraces &&
				((c == '<' && !currentEscapedChar) ||
				(possiblyLinkReferenceInBracesEnd &&
				!(whitespaseCharFunc.apply(c) || c == ')')))))
				notLinkReferenceInBraces = true;
			if (c == '>' && !currentEscapedChar)
				possiblyLinkReferenceInBracesEnd = true;
			if (!prevIsLinkDestinationBegin && isLinkReferenceBegin &&
				c == '<' && !currentEscapedChar)
				possiblyLinkReferenceInBraces = true;
			if (isLinkReferenceBegin &&
				((c >= 0 && c <= 0x20) && !whitespaseCharFunc.apply(c)) ||
				(inParenthesis && c == '(' && !currentEscapedChar))
				notLinkReference = true;
			if (c == '(' && !currentEscapedChar) inParenthesis = true;
			if (c == ')' && !currentEscapedChar) inParenthesis = false;
		}
		if (isLinkReferenceBegin &&
			notLinkReferenceInBraces &&
			notLinkReference) {
			text.append("](");
			sb.setLength(0);
			state = RECOVERY;
		} else if (((c == ')' && !currentEscapedChar) && !isLinkReferenceBegin) ||
			(isLinkReferenceBegin &&
			(whitespaseCharFunc.apply(c) || (c == ')' && !currentEscapedChar)) &&
			((!notLinkReferenceInBraces &&
			possiblyLinkReferenceInBraces &&
			possiblyLinkReferenceInBracesEnd) ||
			(!notLinkReference && !prevInParenthesis &&
			prevIsLinkDestinationBegin)))) {
			state = MAYBE_LINK_TITLE;
			if (!notLinkReferenceInBraces &&
				possiblyLinkReferenceInBraces &&
				possiblyLinkReferenceInBracesEnd)
				linkReference =
					percentEncode(percentDecode(sb.substring(1, sb.length()-1)));
			else
				linkReference =
					percentEncode(percentDecode(sb.toString()));
			sb.setLength(0);
			isLinkTitleBegin = false;
			isLinkTitleEnd = false;
			linkTitleMark = 0;
			reader.repeat();
			currentChar = previousChar;
			currentEscapedChar = previousEscapedChar;
		} else if (isLinkReferenceBegin) {
			sb.append(c);
		}
	}
	
	private void maybeLinkTitleActionHandler() {
		boolean prevIsLinkTitleBegin = isLinkTitleBegin;
		if (!whitespaseCharFunc.apply(c) && c != ')')
			isLinkTitleBegin = true;
		boolean prevIsLinkTitleEnd = isLinkTitleEnd;
		if (isLinkTitleBegin) {
			if (linkTitleMark == c && !currentEscapedChar)
				isLinkTitleEnd = true;
			if (linkTitleMark == 0 &&
				(c == '\'' || c == '"' || c == '(') && !currentEscapedChar)
				linkTitleMark = (c == '(') ? ')' : c;
		}
		if (c == ')' && (!isLinkTitleBegin ||
			(isLinkTitleBegin && prevIsLinkTitleEnd))) {
			insertLinkOrImageNode(linkReference, isLinkTitleBegin ?
				sb.substring(1, sb.length()-1) : "");
			state = UNKNOWN_CHAR;
		} else if (isLinkTitleBegin &&
			((prevIsLinkTitleEnd && !whitespaseCharFunc.apply(c)) ||
			(!prevIsLinkTitleBegin && linkTitleMark == 0))) {
			text.append("](");
			state = RECOVERY;
			sb.setLength(0);
			if (opener instanceof InlineOpenBracketNode)
				replaceNodeByTextNode(opener, "[");
			else
				replaceNodeByTextNode(opener, "![");
		} else if (isLinkTitleBegin && !prevIsLinkTitleEnd) {
			sb.append(c);
		}
	}

	private void maybeLinkLabelActionHandler() {
		if (!linkLabelNWChar &&
			!whitespaseCharFunc.apply(c) &&
			!(c == ']' && !currentEscapedChar))
			linkLabelNWChar = true;
		if ((c == '[' && !currentEscapedChar) ||
			((c == ']' && !currentEscapedChar) &&
			((!linkLabelNWChar &&
			linkLabelLength > 0) || linkLabelLength > 1000))) {
			if (opener instanceof InlineOpenBracketNode)
				replaceNodeByTextNode(opener, "[");
			else
				replaceNodeByTextNode(opener, "![");
			text.append("]");
			processPreviousText();
			InlineOpenBracketNode n = new InlineOpenBracketNode(current);
			n.index(recoveryTextPos);
			current.childs().addLast(n);
			current = n;
			state = RECOVERY;
			sb.setLength(0);
			return;
		} else if (c == ']' && !currentEscapedChar) {
			String rawreflabel;
			if (sb.length() <= 0) {
				int begin;
				if (opener instanceof InlineOpenBracketNode)
					begin = ((InlineOpenBracketNode) opener).index();
				else
					begin = ((InlineBangNode) opener).index();
				rawreflabel = inputString.substring(begin, pos-2);
			} else {
				rawreflabel = sb.toString();
			}
			String reflabel = normalizeReference(rawreflabel, whitespaseCharFunc);
			Link link = linkReferenceMap.get(reflabel);
			if (link == null) {
				if (opener instanceof InlineOpenBracketNode)
					replaceNodeByTextNode(opener, "[");
				else
					replaceNodeByTextNode(opener, "![");
				text.append("]");
				processPreviousText();
				InlineOpenBracketNode n = new InlineOpenBracketNode(current);
				n.index(recoveryTextPos);
				current.childs().addLast(n);
				current = n;
				state = RECOVERY;
				sb.setLength(0);
				return;
			} else {
				insertLinkOrImageNode(link.reference, link.title);
				state = UNKNOWN_CHAR;
			}
		}
		if (currentEscapedChar) sb.append("\\");
		sb.append(c);
		linkLabelLength++;
	}
	
	@Override
	public void parseInline(BlockNode root) {
		inputString = unicodeTrim(root.stringContent());
		if (inputString.isEmpty()) return;
		state = UNKNOWN_CHAR;
		text.setLength(0);
		sb.setLength(0);
		current = root;
		length = inputString.length();
		reader.inputString(inputString);
		reader.unescape(true);
		reader.processEntity(true);
		stack = new LinkedList<>();
		previousChar = 0;
		previousEscapedChar = false;
		processPreviousChar = false;
		finalSpaces = false;
		removeLeadingSpaces = false;
		while (state != FINISH) {
			pos = reader.pos();
			c = reader.next();
			currentChar = c;
			currentEscapedChar = reader.escaped();
			handlersMap.get(state).handle();
			if (state == RECOVERY) {
				if (stack.isEmpty()) {
					reader.pos(recoveryTextPos);
					previousChar = recoveryPreviousChar;
					previousEscapedChar = recoveryPreviousEscapedChar;
					reader.unescape(true);
					reader.processEntity(true);
					state = UNKNOWN_CHAR;
					if (sb.length() > 0)
						text.append(sb.charAt(0));
				} else {
					restoreContext();
				}
				continue;
			}
			previousChar = currentChar;
			previousEscapedChar = currentEscapedChar;
			if (!reader.hasNext()) {
				if (state != UNKNOWN_CHAR) {
					if (recoveryTextPos < length) {
						reader.pos(recoveryTextPos);
						previousChar = recoveryPreviousChar;
						previousEscapedChar = recoveryPreviousEscapedChar;
						reader.unescape(true);
						reader.processEntity(true);
						state = UNKNOWN_CHAR;
						text.append(sb.charAt(0));
					} else {
						text.append(sb);
						state = FINISH;
					}
				} else {
					state = FINISH;
				}
				if (state == FINISH)
					processPreviousText();
			}
		}
		removeOpenBracketOrBangNodes(root);
		processEmphasis(root);
	}
	
	private void rUnknownCharActionHandler() {
		if (c == '[' && !currentEscapedChar) {
				state = MAYBE_LINK_LABEL;
				sb.setLength(0);
				linkLabelLength = 0;
				linkLabelNWChar = false;
		} else {
			state = FINISH;
		}
	}
	
	private void rMaybeLinkLabelActionHandler() {
		if (!linkLabelNWChar &&
			!whitespaseCharFunc.apply(c) &&
			!(c == ']' && !currentEscapedChar))
			linkLabelNWChar = true;
		if ((c == '[' && !currentEscapedChar) ||
			((c == ']' && !currentEscapedChar) &&
			(!linkLabelNWChar || linkLabelLength > 1000))) {
			state = FINISH;
			return;
		} else if (c == ']' && !currentEscapedChar) {
			reflabel = normalizeReference(sb.toString(), whitespaseCharFunc);
			sb.setLength(0);
			state = MAYBE_LINK_REFERENCE_COLON;
			return;
		}
		if (currentEscapedChar) sb.append("\\");
		sb.append(c);
		linkLabelLength++;
	}
	
	private void rMaybeLinkReferenceColonActionHandler() {
		if (c == ':' && !currentEscapedChar) {
			state = MAYBE_LINK_REFERENHCE;
			isLinkReferenceBegin = false;
			possiblyLinkReferenceInBraces = false;
			notLinkReferenceInBraces = false;
			notLinkReference = false;
			possiblyLinkReferenceInBracesEnd = false;
			inParenthesis = false;
			lineEnding = false;
		} else {
			state = FINISH;
		}
	}
	
	private void rMaybeLinkReferenceActionHandler() {
		boolean prevIsLinkDestinationBegin = isLinkReferenceBegin;
		if (!isLinkReferenceBegin &&
			!(c != '\n' && whitespaseCharFunc.apply(c) ||
			!lineEnding && c == '\n'))
			isLinkReferenceBegin = true;
		if (!lineEnding && c == '\n') lineEnding = true;
		if (isLinkReferenceBegin) {
			if ((!prevIsLinkDestinationBegin &&
				isLinkReferenceBegin &&
				!(c == '<' && !currentEscapedChar)) ||
				(possiblyLinkReferenceInBraces &&
				((c == '<' && !currentEscapedChar) ||
				(possiblyLinkReferenceInBracesEnd &&
				!(whitespaseCharFunc.apply(c) || c == ')')))))
				notLinkReferenceInBraces = true;
			if (c == '>' && !currentEscapedChar)
				possiblyLinkReferenceInBracesEnd = true;
			if (!prevIsLinkDestinationBegin && isLinkReferenceBegin &&
				c == '<' && !currentEscapedChar)
				possiblyLinkReferenceInBraces = true;
			if (isLinkReferenceBegin &&
				((c >= 0 && c <= 0x20) && !whitespaseCharFunc.apply(c)) ||
				(inParenthesis && c == '(' && !currentEscapedChar))
				notLinkReference = true;
			if (c == '(' && !currentEscapedChar) inParenthesis = true;
			if (c == ')' && !currentEscapedChar) inParenthesis = false;
		}
		if (isLinkReferenceBegin &&
			notLinkReferenceInBraces &&
			notLinkReference) {
			state = FINISH;
			return;
		} else if (isLinkReferenceBegin &&
			whitespaseCharFunc.apply(c) &&
			((!notLinkReferenceInBraces &&
			possiblyLinkReferenceInBraces &&
			possiblyLinkReferenceInBracesEnd) ||
			(!notLinkReference &&
			prevIsLinkDestinationBegin))) {
			state = MAYBE_LINK_TITLE;
			if (!notLinkReferenceInBraces &&
				possiblyLinkReferenceInBraces &&
				possiblyLinkReferenceInBracesEnd)
				linkReference =
					percentEncode(percentDecode(sb.substring(1, sb.length()-1)));
			else
				linkReference =
					percentEncode(percentDecode(sb.toString()));
			beforeTitlePos = reader.pos();
			sb.setLength(0);
			isLinkTitleBegin = false;
			isLinkTitleEnd = false;
			linkTitleMark = 0;
			reader.repeat();
			lineEnding = false;
			notWsCharAfterTitle = false;
		} else if (isLinkReferenceBegin) {
			sb.append(c);
		}
	}
	
	private void rMaybeLinkTitleActionHandler() {
		boolean prevIsLinkTitleBegin = isLinkTitleBegin;
		if (!isLinkTitleBegin &&
			!(c != '\n' && whitespaseCharFunc.apply(c) ||
			!lineEnding && c == '\n'))
			isLinkTitleBegin = true;
		boolean prevLineEnding = lineEnding;
		if (!lineEnding && c == '\n') {
			lineEnding = true;
			beforeTitlePos = reader.pos();
		}
		boolean prevIsLinkTitleEnd = isLinkTitleEnd;
		if (isLinkTitleBegin) {
			if (linkTitleMark == c && !currentEscapedChar)
				isLinkTitleEnd = true;
			if (linkTitleMark == 0 &&
				(c == '\'' || c == '"' || c == '(') && !currentEscapedChar)
				linkTitleMark = (c == '(') ? ')' : c;
		}
		if (isLinkTitleBegin && prevIsLinkTitleEnd && !whitespaseCharFunc.apply(c))
			notWsCharAfterTitle = true;
		if (!lineEnding && (notWsCharAfterTitle || isLinkTitleBegin &&
			!prevIsLinkTitleBegin && linkTitleMark == 0)) {
			state = FINISH;
			return;
		} else if ((c == '\n' && isLinkTitleBegin && prevIsLinkTitleEnd) ||
			(isLinkTitleBegin && !prevIsLinkTitleBegin && linkTitleMark == 0) ||
			(!isLinkTitleBegin && ((prevLineEnding &&
			c == '\n') || !reader.hasNext()))) {
			boolean withTitle = isLinkTitleBegin && linkTitleMark != 0 && !notWsCharAfterTitle;
			if (!linkReferenceMap.containsKey(reflabel)) {
				Link link = new Link();
				link.reference = linkReference;
				link.title = withTitle ? sb.substring(1, sb.length()-1) : "";
				linkReferenceMap.put(reflabel, link);
			}
			hasReferenceDefs = true;
			if (!withTitle) reader.pos(beforeTitlePos);
			state = UNKNOWN_CHAR;
			referenceDefEndPos = withTitle ? pos : beforeTitlePos;
		} else if (isLinkTitleBegin && !prevIsLinkTitleEnd) {
			sb.append(c);
		}
	}

	@Override
	public int parseReferences(String input) {
		length = input.length();
		reader.inputString(input);
		reader.unescape(true);
		reader.processEntity(true);
		state = UNKNOWN_CHAR;
		hasReferenceDefs = false;
		referenceDefEndPos = 0;
		sb.setLength(0);
		while (state != FINISH) {
			pos = reader.pos();
			c = reader.next();
			currentEscapedChar = reader.escaped();
			rHandlersMap.get(state).handle();
			if (!reader.hasNext()) state = FINISH;
		}
		return hasReferenceDefs ? referenceDefEndPos : -1;
	}
	
	private void otMaybeTagAttributesOrEndOpenTagActionHandler() {
		if (c == '>') {
			if (!reader.hasNext()) {
				state = FINISH;
			} else {
				state = UNKNOWN_CHAR;
			}
			return;
		} else if ((whitespaseCharFunc.apply(c) || c == '/') &&
			previousChar != '/' ) {
		} else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
			c == '_' || c == ':') {
			state = MAYBE_TAG_ATTRIBUTES;
			reader.repeat();
			currentChar = previousChar;
			isFirstTagAttributeChar = true;
			openTagWhitespace = false;
		} else {
			state = RECOVERY;
		}
	}
	
	private void otUnknownCharActionHandler() {
		if (!whitespaseCharFunc.apply(c))
			state = RECOVERY;
		else if (!reader.hasNext())
			state = FINISH;
	}
	
	@Override
	public boolean isOpenTag(String input, int beginPos) {
		inputString = input;
		length = inputString.length();
		reader.inputString(inputString);
		reader.unescape(false);
		reader.processEntity(false);
		reader.pos(beginPos);
		state = MAYBE_OPEN_TAG;
		sb.setLength(0);
		isFirstOpenTagChar = true;
		previousChar = '<';
		while (state != FINISH) {
			pos = reader.pos();
			c = reader.next();
			if (c == 0) c = '\uFFFD';
			currentChar = c;
			otHandlersMap.get(state).handle();
			if (state == RECOVERY) return false;
			previousChar = currentChar;
			if (!reader.hasNext() && state != FINISH) return false;
		}
		return true;
	}
	
	@Override
	public boolean isClosingTag(String input, int beginPos) {
		inputString = input;
		length = inputString.length();
		pos = beginPos;
		while (pos < length) {
			c = inputString.charAt(pos);
			if (c == 0) c = '\uFFFD';
			if (whitespaseCharFunc.apply(c) || c == '>')
				break;
			else if (!(c >= '0' && c <= '9' ||
				c >= 'a' && c <= 'z' ||
				c >= 'A' && c <= 'Z' ||
				c == '-'))
				return false;
			pos++;
		}
		pos++;
		if (c != '>') {
			while (pos < length) {
				c = inputString.charAt(pos);
				if (c == 0) c = '\uFFFD';
				if (c == '>')
					break;
				else if (!whitespaseCharFunc.apply(c))
					return false;
				pos++;
			}
		}
		pos++;
		if (pos < length) {
			while (pos < length) {
				c = inputString.charAt(pos);
				if (c == 0) c = '\uFFFD';
				if (!whitespaseCharFunc.apply(c))
					return false;
				pos++;
			}
		}
		return true;
	}
}

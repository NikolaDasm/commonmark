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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static nikoladasm.commonmark.CharReaderImpl.State.*;

public class CharReaderImpl implements CharReader {

	private static class CharBuffers {
		private char[] buff1 = new char[1];
		private char[] buff2 = new char[2];
		private char[] buff3 = new char[3];
		private char[] buff4 = new char[4];
	}

	protected static enum State {
		UNKNOWN_CHAR,
		MAYBE_ESCAPING_CHAR,
		MAYBE_ENTITY,
		MAYBE_NUMERIC_ENTITY,
		MAYBE_NAMED_ENTITY,
		MAYBE_BEGIN_HEX_NUMERIC_ENTITY,
		MAYBE_DEC_NUMERIC_ENTITY,
		MAYBE_HEX_NUMERIC_ENTITY,
		FINISH
	}
	
	@FunctionalInterface
	private static interface CharReaderHandler {
		void handle();
	}
	
	private static final String ENTITIES_PROPERTY_FILE =
		"resources/entities.properies";
	private static final String ESCAPABLE = "\\!\"#$%&'()*+,./:;<=>?@[]^_`{|}~-";
	private static final Function<Character,Boolean> DEFAULT_ESCAPABLE_CHAR_FUNC =
		c -> ESCAPABLE.contains(String.valueOf(c));
	
	private static AtomicReference<Properties> entities = new AtomicReference<>();
	private static Map<String,char[]> entityMap = new ConcurrentHashMap<>();
	private static ThreadLocal<CharBuffers> charBuffers = new ThreadLocal<>();

	private static int codePointToCharBuffers(int codePoint, CharBuffers buffs, int charCount) {
		char lead, trail;
		if (codePoint < 65536) {
			lead = (char)codePoint;
			if (charCount < 1) buffs.buff1[charCount] = lead;
			if (charCount < 2) buffs.buff2[charCount] = lead;
			if (charCount < 3) buffs.buff3[charCount] = lead;
			buffs.buff4[charCount] = lead;
			charCount++;
		} else {
			lead = (char)((codePoint >>> 10) + 55232);
			trail = (char)((codePoint & 1023) + 56320);
			if (charCount < 1) buffs.buff1[charCount] = lead;
			if (charCount < 2) buffs.buff2[charCount] = lead;
			if (charCount < 3) buffs.buff3[charCount] = lead;
			buffs.buff4[charCount] = lead;
			charCount++;
			if (charCount < 1) buffs.buff1[charCount] = trail;
			if (charCount < 2) buffs.buff2[charCount] = trail;
			if (charCount < 3) buffs.buff3[charCount] = trail;
			buffs.buff4[charCount] = trail;
			charCount++;
		}
		return charCount;
	}
	
	private static char[] decodeEntity(String entity) {
		if (!entityMap.containsKey(entity)) {
			if (entities.get() == null) {
				Properties ent = new Properties();
				InputStream is = CharReaderImpl.class.getResourceAsStream(ENTITIES_PROPERTY_FILE);
				if (is == null)
					is = CharReaderImpl.class.getClassLoader().getResourceAsStream(ENTITIES_PROPERTY_FILE);
				if (is == null) throw new RuntimeException("File \"entities.properties\" not found.");
				try {
					ent.load(is);
					is.close();
				} catch (IOException e) {
					throw new RuntimeException("Could not load file \"entities.properties\"");
				}
				entities.set(ent);
			}
			Properties ent = entities.get();
			if (!ent.containsKey(entity))
				return null;
			if (charBuffers.get() == null)
				charBuffers.set(new CharBuffers());
			CharBuffers cbuff = charBuffers.get();
			String strCodePoint = ent.getProperty(entity);
			int charCount = 0;
			if (strCodePoint.contains(",")) {
				StringBuilder sb = new StringBuilder();
				char c;
				int length = strCodePoint.length();
				for (int i=0; i < length; i++) {
					c = strCodePoint.charAt(i);
					if (c == ',' || i >= length-1) {
						if (c != ',') sb.append(c);
						String num = sb.toString();
						sb = new StringBuilder();
						if (num.isEmpty()) continue;
						int codePoint = Integer.valueOf(num);
						charCount = codePointToCharBuffers(codePoint, cbuff, charCount);
					} else {
						sb.append(c);
					}
				}
				switch (charCount) {
					case 1: {
						entityMap.put(entity, cbuff.buff1);
						cbuff.buff1 = new char[1];
						break;
					}
					case 2: {
						entityMap.put(entity, cbuff.buff2);
						cbuff.buff2 = new char[2];
						break;
					}
					case 3: {
						entityMap.put(entity, cbuff.buff3);
						cbuff.buff3 = new char[3];
						break;
					}
					case 4: {
						entityMap.put(entity, cbuff.buff4);
						cbuff.buff4 = new char[4];
						break;
					}
				}
			} else {
				int codePoint = Integer.valueOf(strCodePoint);
				char[] chars = Character.toChars(codePoint);
				entityMap.put(entity, chars);
			}
		}
		return entityMap.get(entity);
	}
	
	private State state;
	private int charIndex = 0;
	private boolean empty = true;
	private String inputString;
	private int length;
	private char c;
	private int pos;
	private int newPos;
	private boolean unescape;
	private boolean processEntity;
	private int entityBeginPos;
	private int entityLength;
	private boolean escaped;
	private Function<Character,Boolean> escapableCharFunc;
	private char next;
	private char[] chars;
	private boolean repeat;

	private Map<State,CharReaderHandler> handlersMap;
	
	public CharReaderImpl() {
		escapableCharFunc = DEFAULT_ESCAPABLE_CHAR_FUNC;
		charIndex = 0;
		handlersMap = new HashMap<>();
		handlersMap.put(UNKNOWN_CHAR, this::unknownCharActionHandler);
		handlersMap.put(MAYBE_ESCAPING_CHAR, this::maybeEscapingCharHandler);
		handlersMap.put(MAYBE_ENTITY, this::maybeEntityHandler);
		handlersMap.put(MAYBE_NUMERIC_ENTITY, this::maybeNumericEntityHandler);
		handlersMap.put(MAYBE_BEGIN_HEX_NUMERIC_ENTITY, this::maybeBeginHexNumericEntityHandler);
		handlersMap.put(MAYBE_HEX_NUMERIC_ENTITY, this::maybeHexNumericEntityHandler);
		handlersMap.put(MAYBE_DEC_NUMERIC_ENTITY, this::maybeDecNumericEntityHandler);
		handlersMap.put(MAYBE_NAMED_ENTITY, this::maybeNamedEntityHandler);
	}
	
	@Override
	public int pos() {
		return pos;
	}
	
	@Override
	public void pos(int pos) {
		this.pos = pos;
		charIndex = 0;
		empty = true;
		repeat = false;
	}
	
	@Override
	public void inputString(String str) {
		inputString = str;
		length = str.length();
		charIndex = 0;
		empty = true;
		repeat = false;
		pos = 0;
	}
	
	@Override
	public void unescape(boolean unescape) {
		this.unescape = unescape;
	}
	
	@Override
	public boolean isUnescape() {
		return unescape;
	}
	
	@Override
	public void processEntity(boolean processEntity) {
		this.processEntity = processEntity;
	}
	
	@Override
	public boolean isProcessEntity() {
		return processEntity;
	}
	
	@Override
	public boolean escaped() {
		return escaped;
	}
	
	@Override
	public void escapableCharFunc(Function<Character,Boolean> escapableCharFinc) {
		this.escapableCharFunc = escapableCharFinc;
	}
	
	@Override
	public void repeat() {
		repeat = true;
	}
	
	@Override
	public boolean hasNext() {
		return repeat || pos < length;
	}
	
	private void unknownCharActionHandler() {
		if (c == '\\' && unescape) {
			state = MAYBE_ESCAPING_CHAR;
		} else if (c == '&' && processEntity) {
			state = MAYBE_ENTITY;
			entityBeginPos = newPos;
		} else {
			state = State.FINISH;
			next = c;
		}
	}

	private void maybeEscapingCharHandler() {
		if (escapableCharFunc.apply(c)) {
			state = FINISH;
			next = c;
			escaped = true;
		} else {
			state = FINISH;
			next = '\\';
			newPos--;
		}
	}
	
	private void maybeEntityHandler() {
		if (c == '#') {
			state = MAYBE_NUMERIC_ENTITY;
		} else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
			state = MAYBE_NAMED_ENTITY;
		} else {
			state = FINISH;
			newPos = entityBeginPos;
			next = '&';
		}
	}

	private void maybeNumericEntityHandler() {
		if (c == 'x' || c == 'X') {
			state = MAYBE_BEGIN_HEX_NUMERIC_ENTITY;
		} else if (c >= '0' && c <= '9') {
			state = MAYBE_DEC_NUMERIC_ENTITY;
		} else {
			state = FINISH;
			newPos = entityBeginPos;
			next = '&';
		}
	}

	private void maybeBeginHexNumericEntityHandler() {
		if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
			state = MAYBE_HEX_NUMERIC_ENTITY;
		} else {
			state = FINISH;
			newPos = entityBeginPos;
			next = '&';
		}
	}

	private void maybeHexNumericEntityHandler() {
		entityLength = entityBeginPos-newPos-3;
		if (c == ';' && entityLength <= 8) {
			int codePoint = Integer.valueOf(inputString.substring(entityBeginPos+3, newPos), 16);
			if (!Character.isValidCodePoint(codePoint) || codePoint == 0) codePoint = 0xfffd;
			char[] ch = Character.toChars(codePoint);
			if (ch.length > 1) {
				next = ch[0];
				chars = ch;
				charIndex = 1;
				empty = false;
			} else {
				next = ch[0];
				empty = true;
			}
			if (escapableCharFunc.apply(next))
				escaped = true;
			state = FINISH;
		} else if (!(((c >= '0' && c <= '9') ||
			(c >= 'a' && c <= 'f') ||
			(c >= 'A' && c <= 'F')) && entityLength < 8)) {
			state = FINISH;
			newPos = entityBeginPos;
			next = '&';
		}
	}

	private void maybeDecNumericEntityHandler() {
		entityLength = entityBeginPos-newPos-2;
		if (c == ';' && entityLength <= 8) {
			int codePoint = Integer.valueOf(inputString.substring(entityBeginPos+2, newPos));
			if (!Character.isValidCodePoint(codePoint) || codePoint == 0) codePoint = 0xfffd;
			char[] ch = Character.toChars(codePoint);
			if (ch.length > 1) {
				next = ch[0];
				chars = ch;
				charIndex = 1;
				empty = false;
			} else {
				next = ch[0];
				empty = true;
			}
			if (escapableCharFunc.apply(next))
				escaped = true;
			state = FINISH;
		} else if (!((c >= '0' && c <= '9') && entityLength < 8)) {
			state = FINISH;
			newPos = entityBeginPos;
			next = '&';
		}
	}

	private void maybeNamedEntityHandler() {
		entityLength = entityBeginPos-newPos-1;
		if (c == ';' && entityLength <= 32) {
			char[] ch = decodeEntity(inputString.substring(entityBeginPos, newPos+1));
			if (ch == null) {
				newPos = entityBeginPos;
				next = '&';
				empty = true;
			} else if (ch.length > 1) {
				next = ch[0];
				chars = ch;
				charIndex = 1;
				empty = false;
			} else {
				next = ch[0];
				empty = true;
			}
			if (ch != null && escapableCharFunc.apply(next))
				escaped = true;
			state = FINISH;
		} else if (!(((c >= 'a' && c <= 'z') ||
			(c >= 'A' && c <= 'Z') ||
			(c >= '0' && c <= '9')) && entityLength < 32)) {
			state = FINISH;
			newPos = entityBeginPos;
			next = '&';
		}
	}
	
	@Override
	public char next() {
		if (repeat) {
			repeat = false;
			return next;
		}
		if (!empty) {
			next = chars[charIndex];
			charIndex++;
			if (charIndex >= chars.length) {
				empty = true;
				charIndex = 0;
			}
		} else {
			state = UNKNOWN_CHAR;
			newPos = pos;
			escaped = false;
			while (state != FINISH) {
				c = inputString.charAt(newPos);
				handlersMap.get(state).handle();
				newPos++;
				if (newPos >= length) {
					if (state == MAYBE_ESCAPING_CHAR) {
						next = '\\';
					} else if (state != FINISH) {
						newPos = entityBeginPos+1;
						next = '&';
					}
					state = FINISH;
				}
			}
		}
		this.pos = empty ? newPos : pos;
		return next;
	}
}

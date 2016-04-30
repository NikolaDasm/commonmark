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

import static java.nio.charset.StandardCharsets.UTF_8;

public class UrlUtil {

	private static char[] hexDigit = "0123456789ABCDEF".toCharArray();

	private static String[] replaceMap;
	
	static {
		replaceMap = new String[128];
		for (int i = 0; i< 128; i++) {
			if ((i >= '0' && i <= '9') ||
				(i >= 'a' && i <= 'z') ||
				(i >= 'A' && i <= 'Z') ||
				";/?:@&=+$,-_.!~*'()#".indexOf(i) >= 0) {
				replaceMap[i] = String.valueOf((char)i);
			} else {
				replaceMap[i] = "%" + hexDigit[i >>> 4] + hexDigit[i & 15];
			}
		}
	}
	
	public static String percentDecode(String in) {
		int length = in.length();
		StringBuilder out = new StringBuilder();
		int beginIndex = length;
		int endIndex = length;
		int nextIndex = length;
		boolean decodeByte = false;
		boolean firstUtf8Byte = true;
		int utf8ByteCount = 0;
		int cp = 0;
		int t = 0;
		int i = 0;
		while (i < length) {
			char c = in.charAt(i);
			if (!decodeByte && c == '%') {
				decodeByte = true;
				beginIndex = i+1;
				endIndex = i+2;
				nextIndex = i+3;
				t = 0;
				i++;
				continue;
			}
			if ((i == beginIndex || i == endIndex) &&
				!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f'))) {
				if (!firstUtf8Byte) {
					firstUtf8Byte = true;
					out.appendCodePoint(0xFFFD);
				}
				decodeByte = false;
				i = beginIndex;
				out.append('%');
				beginIndex = length;
				endIndex = length;
				nextIndex = length;
				continue;
			}
			if (i == beginIndex) {
				t = ((c > '9') ? (c & 223) - 'A' + 10 : c - '0');
			} else if (i == endIndex) {
				t = ((t << 4) | ((c > '9') ? (c & 223) - 'A' + 10 : c - '0'));
				if (firstUtf8Byte) {
					if ((t & 128) == 0) {
						if (";/?:@&=+$,#".indexOf(t) >= 0) {
							decodeByte = false;
							out.append("%" + hexDigit[(t & 255) >>> 4] + hexDigit[t & 15]);
							beginIndex = length;
							endIndex = length;
							nextIndex = length;
							i++;
							continue;
						}
						out.appendCodePoint(t);
					} else {
						firstUtf8Byte = false;
						if ((t & 224) == 192) {
							utf8ByteCount = 1;
							cp = t & 31;
						} else if ((t & 240) == 224) {
							utf8ByteCount = 2;
							cp = t & 15;
						} else if ((t & 248) == 240) {
							utf8ByteCount = 3;
							cp = t & 7;
						} else {
							firstUtf8Byte = true;
							out.appendCodePoint(0xFFFD);
						}
					}
				} else {
					if ((t & 192) != 128) {
						firstUtf8Byte = true;
						out.appendCodePoint(0xFFFD);
						decodeByte = false;
						i = beginIndex-1;
						beginIndex = length;
						endIndex = length;
						nextIndex = length;
						continue;
					}
					cp = (cp << 6) | t & 63;
					utf8ByteCount--;
					if (utf8ByteCount <= 0) {
						firstUtf8Byte = true;
						out.appendCodePoint(cp);
					}
				}
			} else if (!firstUtf8Byte && i == nextIndex && !decodeByte) {
				firstUtf8Byte = true;
				out.appendCodePoint(0xFFFD);
			}
			if (!decodeByte) out.append(c);
			if (decodeByte && i >= endIndex)
				decodeByte = false;
			i++;
		}
		return out.toString();
	}
	
	public static String percentEncode(String in) {
		StringBuilder sb = new StringBuilder();
		int i = 0;
		int length = in.length();
		boolean encodedByte = false;
		int beginIndex = length;
		int cp1 = 0;
		while (i < length) {
			int cp = in.codePointAt(i);
			if (!encodedByte && cp == '%') {
				encodedByte = true;
				beginIndex = i+1;
				i++;
				continue;
			}
			if (encodedByte) {
				if (i < beginIndex + 2 &&
					!((cp >= '0' && cp <= '9') || (cp >= 'A' && cp <= 'F'))) {
					encodedByte = false;
					cp = '%';
					i = beginIndex;
					beginIndex = length;
				} else {
					if (i >= beginIndex + 1) {
						encodedByte = false;
						sb.append('%').appendCodePoint(cp1).appendCodePoint(cp);
					} else {
						cp1 = cp;
					}
					i++;
					continue;
				}
			}
			if (cp < 128) {
				sb.append(replaceMap[cp]);
				i++;
				continue;
			}
			byte[] utf8 = new String(new int[]{cp}, 0, 1).getBytes(UTF_8);
			for (int j = 0; j < utf8.length; j++)
				sb.append("%" + hexDigit[(utf8[j] & 255) >>> 4] + hexDigit[utf8[j] & 15]);
			i++;
		}
		return sb.toString();
	}
}

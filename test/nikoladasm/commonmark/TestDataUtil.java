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
import static java.nio.file.StandardOpenOption.READ;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class TestDataUtil {
	
	private static final Pattern TEST_DATA_HEADER = Pattern.compile("^\\s*\\{%%%([^%]+)%%%\\}\\s*$");
	
	public static class TestDataContainer {
		public String name;
		public String input;
		public String output;
	}
	
	private static BufferedReader fileReader(Path filePath) throws IOException {
		return new BufferedReader(
			new InputStreamReader(
				Files.newInputStream(
					filePath, READ), UTF_8));
	}
	
	private static boolean processHeader(BufferedReader reader, TestDataContainer container, boolean check) throws IOException {
		String line;
		while ((line = reader.readLine()) != null) {
			Matcher matcher = TEST_DATA_HEADER.matcher(line);
			if (check) {
				if (matcher.matches() &&
					container.name.equalsIgnoreCase(matcher.group(1))) {
					return true;
				}
			} else {
				if (matcher.matches()) {
					container.name = matcher.group(1);
					return true;
				}
			}
		}
		return false;
	}

	private static String processData(BufferedReader reader, StringBuffer sb) throws IOException {
		String line;
		boolean firstLine = true;
		while ((line = reader.readLine()) != null) {
			Matcher matcher = TEST_DATA_HEADER.matcher(line);
			if (matcher.matches()) {
				return matcher.group(1);
			}
			if (!firstLine) sb.append("\n");
			sb.append(line);
			firstLine = false;
		}
		return null;
	}
	
	public static List<TestDataContainer> readTestData(Path inputFile, Path outputFile) {
		List<TestDataContainer> result = new LinkedList<>();
		try{
			BufferedReader inputReader = fileReader(inputFile);
			BufferedReader outputReader = fileReader(outputFile);
			String inputTestDataName = null;
			String outputTestDataName = null;
			do {
				TestDataContainer container = new TestDataContainer();
				if (inputTestDataName == null) {
					boolean inputDataBegin = processHeader(inputReader, container, false);
					if (!inputDataBegin) {
						inputReader.close();
						outputReader.close();
						return result;
					}
				} else {
					container.name = inputTestDataName;
					inputTestDataName = null;
				}
				if (outputTestDataName == null) {
					boolean outputDataBegin = processHeader(outputReader, container, true);
					if (!outputDataBegin)
						throw new RuntimeException("Invalid test data");
				} else {
					if (!container.name.equalsIgnoreCase(outputTestDataName))
						throw new RuntimeException("Invalid test data");
				}
				StringBuffer sb = new StringBuffer();
				inputTestDataName = processData(inputReader, sb);
				container.input = sb.toString();
				sb = new StringBuffer();
				outputTestDataName = processData(outputReader, sb);
				container.output = sb.toString();
				result.add(container);
			} while (inputReader.ready());
			inputReader.close();
			outputReader.close();
			return result;
		} catch (IOException e) {
			throw new RuntimeException("Can't read test data", e);
		}
	}
	
	private static String inputStreamToString(InputStream stream) throws IOException {
		final char[] buffer = new char[2048];
		StringBuilder sb = new StringBuilder();
		Reader reader = new InputStreamReader(stream, UTF_8); 
		int rsz;
		while ((rsz = reader.read(buffer, 0, buffer.length)) >= 0)
			sb.append(buffer, 0, rsz);
		return sb.toString();
	}
	
	public static List<TestDataContainer> readCompressedTestData(String inputFileName, String outputFileName) {
		List<TestDataContainer> result = new LinkedList<>();
		try {
			ZipFile inputArchive = new ZipFile(inputFileName);
			ZipFile outputArchive = new ZipFile(outputFileName);
			Enumeration<? extends ZipEntry> entries = inputArchive.entries();
			while(entries.hasMoreElements()) {
				ZipEntry inputEntry = entries.nextElement();
				if (inputEntry.isDirectory()) continue;
				ZipEntry outputEntry = outputArchive.getEntry(inputEntry.getName()+".html");
				TestDataContainer container = new TestDataContainer();
				container.name = inputEntry.getName();
				container.input = inputStreamToString(inputArchive.getInputStream(inputEntry));
				container.output = inputStreamToString(outputArchive.getInputStream(outputEntry));
				result.add(container);
			}
			inputArchive.close();
			outputArchive.close();
			return result;
		} catch (Exception e) {
			throw new RuntimeException("Can't read test data", e);
		}
	}
}

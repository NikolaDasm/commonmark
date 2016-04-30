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

package nikoladasm.commonmark.inlineparser;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import nikoladasm.commonmark.UnescapingStringUnitTest;

@Suite.SuiteClasses( 
		{UnescapingStringUnitTest.class,
		FirstChildInlineCodeSpanUnitTest.class,
		SecondChildInlineCodeSpanUnitTest.class,
		EmailAutolinkUnitTest.class,
		IncorrectEmailAutolinkUnitTest.class,
		ClosingTagUnitTest.class,
		IncorrectClosingTagUnitTest.class,
		CdataTagUnitTest.class,
		IncorrectCdataTagUnitTest.class,
		CommentTagUnitTest.class,
		IncorrectCommentTagUnitTest.class,
		DeclarationTagUnitTest.class,
		IncorrectDeclarationTagUnitTest.class,
		ProcessingInstructionUnitTest.class,
		IncorrectProcessingInstructionUnitTest.class,
		AutolinkUnitTest.class,
		IncorrectAutolinkUnitTest.class,
		OpenTagUnitTest.class,
		IncorrectOpenTagUnitTest.class,
		InlineLinkUnitTest.class,
		IncorrectInlineLinkUnitTest.class,
		FullReferenceLinkUnitTest.class,
		CollapsedReferenceLinkUnitTest.class,
		ShortcutReferenceLinkUnitTest.class,
		IncorrectFullReferenceLinkUnitTest.class,
		InlineImageUnitTest.class,
		InlineEmphasisUnitTest.class,
		InlineStrongEmphasisUnitTest.class,
		InlineLineBreakUnitTest.class,
		InlineSoftBreakUnitTest.class,
		InlineTextUnitTest.class}
)

@RunWith(Suite.class)
public class InlineParserTestSuite {
}

/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.core.site.archive;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FoolFuukaReaderTest {
    @Test
    public void quotesBecomeQuoteLinks() {
        assertEquals("<a href=\"#p123\" class=\"quotelink\">&gt;&gt;123</a><br>yes",
                FoolFuukaReader.toHtml(">>123\nyes"));
    }

    @Test
    public void greentextIsWrapped() {
        assertEquals("<span class=\"quote\">&gt;be me</span><br>ok",
                FoolFuukaReader.toHtml(">be me\nok"));
    }

    @Test
    public void htmlIsEscaped() {
        assertEquals("&lt;b&gt;not bold&lt;/b&gt; &amp; &quot;quoted&quot;",
                FoolFuukaReader.toHtml("<b>not bold</b> & \"quoted\""));
    }

    @Test
    public void bbCodeBecomesTags() {
        assertEquals("<s>secret</s> <pre class=\"prettyprint\">x = 1</pre>",
                FoolFuukaReader.toHtml("[spoiler]secret[/spoiler] [code]x = 1[/code]"));
    }

    @Test
    public void missingCommentIsEmpty() {
        assertEquals("", FoolFuukaReader.toHtml(null));
        assertEquals("", FoolFuukaReader.toHtml(""));
    }

    @Test
    public void windowsLineEndings() {
        assertEquals("a<br>b", FoolFuukaReader.toHtml("a\r\nb"));
    }
}

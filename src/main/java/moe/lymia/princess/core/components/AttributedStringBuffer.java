/*
 * Copyright (c) 2017 Lymia Alusyia <lymia@lymiahugs.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

// Original license:
/*
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package moe.lymia.princess.core.components;

import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This class is used to build an AttributedString.
 */
class AttributedStringBuffer {
    /**
     * The strings.
     */
    private List<String> strings;

    /**
     * The attributes.
     */
    private List<Map<AttributedCharacterIterator.Attribute, Object>> attributes;

    /**
     * The number of items.
     */
    private int count;

    /**
     * The length of the attributed string.
     */
    private int length;

    /**
     * Creates a new empty AttributedStringBuffer.
     */
    public AttributedStringBuffer() {
        strings = new ArrayList<>();
        attributes = new ArrayList<>();
        count = 0;
        length = 0;
    }

    /**
     * Tells whether this AttributedStringBuffer is empty.
     */
    public boolean isEmpty() {
        return count == 0;
    }

    /**
     * Returns the length in chars of the current Attributed String
     */
    public int length() {
        return length;
    }

    /**
     * Appends a String and its associated attributes.
     */
    public void append(String s, Map<AttributedCharacterIterator.Attribute, Object> m) {
        if (s.length() == 0) return;
        strings.add(s);
        attributes.add(m);
        count++;
        length += s.length();
    }

    /**
     * Returns the value of the last char or -1.
     */
    public int getLastChar() {
        if (count == 0) {
            return -1;
        }
        String s = strings.get(count - 1);
        return s.charAt(s.length() - 1);
    }

    /**
     * Strips the last string character.
     */
    public void stripFirst() {
        String s = strings.get(0);
        if (s.charAt(s.length() - 1) != ' ')
            return;

        length--;

        if (s.length() == 1) {
            attributes.remove(0);
            strings.remove(0);
            count--;
            return;
        }

        strings.set(0, s.substring(1));
    }

    /**
     * Strips the last string character.
     */
    public void stripLast() {
        String s = strings.get(count - 1);
        if (s.charAt(s.length() - 1) != ' ')
            return;

        length--;

        if (s.length() == 1) {
            attributes.remove(--count);
            strings.remove(count);
            return;
        }

        strings.set(count - 1, s.substring(0, s.length() - 1));
    }

    /**
     * Builds an attributed string from the content of this
     * buffer.
     */
    public AttributedString toAttributedString() {
        switch (count) {
            case 0:
                return null;
            case 1:
                return new AttributedString(strings.get(0),
                        attributes.get(0));
        }

        StringBuilder sb = new StringBuilder(strings.size() * 5);
        for (String string : strings) {
            sb.append(string);
        }

        AttributedString result = new AttributedString(sb.toString());

        // Set the attributes

        Iterator<String> sit = strings.iterator();
        Iterator<Map<AttributedCharacterIterator.Attribute, Object>> ait = attributes.iterator();
        int idx = 0;
        while (sit.hasNext()) {
            String s = sit.next();
            int nidx = idx + s.length();
            Map<AttributedCharacterIterator.Attribute, Object> m = ait.next();
            Iterator<AttributedCharacterIterator.Attribute> kit = m.keySet().iterator();
            Iterator vit = m.values().iterator();
            while (kit.hasNext()) {
                AttributedCharacterIterator.Attribute attr = kit.next();
                Object val = vit.next();
                result.addAttribute(attr, val, idx, nidx);
            }
            idx = nidx;
        }

        return result;
    }

    public String toString() {
        switch (count) {
            case 0:
                return "";
            case 1:
                return strings.get(0);
        }

        StringBuilder sb = new StringBuilder(strings.size() * 5);
        for (Object string : strings) {
            sb.append((String) string);
        }
        return sb.toString();
    }
}

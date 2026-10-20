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
package org.floens.chan.core.cloverimport;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Reads and writes the XML file Android stores SharedPreferences in.
 * <p>Values are String, Boolean, Integer, Long, Float or Set&lt;String&gt;.
 */
public class SharedPreferencesXml {
    public static Map<String, Object> parse(byte[] xml) throws IOException {
        Element root;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setExpandEntityReferences(false);
            root = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml))
                    .getDocumentElement();
        } catch (Exception e) {
            throw new IOException("Invalid preferences XML", e);
        }

        Map<String, Object> values = new LinkedHashMap<>();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            String name = element.getAttribute("name");
            if (name.isEmpty()) {
                continue;
            }

            try {
                Object value = parseValue(element);
                if (value != null) {
                    values.put(name, value);
                }
            } catch (NumberFormatException ignored) {
                // Skip malformed values
            }
        }
        return values;
    }

    private static Object parseValue(Element element) {
        String value = element.getAttribute("value");
        switch (element.getTagName()) {
            case "string":
                return element.getTextContent();
            case "boolean":
                return Boolean.parseBoolean(value);
            case "int":
                return Integer.parseInt(value);
            case "long":
                return Long.parseLong(value);
            case "float":
                return Float.parseFloat(value);
            case "set":
                Set<String> set = new HashSet<>();
                NodeList items = element.getElementsByTagName("string");
                for (int i = 0; i < items.getLength(); i++) {
                    set.add(items.item(i).getTextContent());
                }
                return set;
            default:
                return null;
        }
    }

    public static byte[] write(Map<String, ?> values) {
        StringBuilder xml = new StringBuilder("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n");
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String name = escape(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof String) {
                xml.append("    <string name=\"").append(name).append("\">")
                        .append(escape((String) value)).append("</string>\n");
            } else if (value instanceof Set) {
                xml.append("    <set name=\"").append(name).append("\">\n");
                for (Object item : (Set<?>) value) {
                    xml.append("        <string>").append(escape(String.valueOf(item))).append("</string>\n");
                }
                xml.append("    </set>\n");
            } else if (value != null) {
                String tag = tagName(value);
                if (tag != null) {
                    xml.append("    <").append(tag).append(" name=\"").append(name)
                            .append("\" value=\"").append(value).append("\" />\n");
                }
            }
        }
        xml.append("</map>\n");
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String tagName(Object value) {
        if (value instanceof Boolean) {
            return "boolean";
        } else if (value instanceof Integer) {
            return "int";
        } else if (value instanceof Long) {
            return "long";
        } else if (value instanceof Float) {
            return "float";
        }
        return null;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

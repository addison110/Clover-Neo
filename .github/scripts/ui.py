#!/usr/bin/env python3
"""Prints the center of the first view in a uiautomator dump whose attribute matches a regex.

Usage: ui.py DUMP.xml ATTRIBUTE REGEX
Exits with 1 when no view matches.
"""
import re
import sys
import xml.etree.ElementTree as ET

path, attribute, pattern = sys.argv[1:4]
regex = re.compile(pattern, re.IGNORECASE)

try:
    root = ET.parse(path).getroot()
except (ET.ParseError, FileNotFoundError):
    sys.exit(1)

for node in root.iter('node'):
    if regex.search(node.get(attribute, '')):
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds', '')))
        if x2 > x1 and y2 > y1:
            print((x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit(0)

sys.exit(1)

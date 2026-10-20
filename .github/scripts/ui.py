#!/usr/bin/env python3
"""Prints the center of a view in a uiautomator dump whose attribute matches a regex.

Usage: ui.py DUMP.xml ATTRIBUTE REGEX [left|last]
With "left" it prints the middle of the space left of the view instead, with "last" it uses the
last matching view.
Exits with 1 when no view matches.
"""
import re
import sys
import xml.etree.ElementTree as ET

path, attribute, pattern = sys.argv[1:4]
option = sys.argv[4] if len(sys.argv) > 4 else ''
left = option == 'left'
regex = re.compile(pattern, re.IGNORECASE)

try:
    root = ET.parse(path).getroot()
except (ET.ParseError, FileNotFoundError):
    sys.exit(1)

found = None
for node in root.iter('node'):
    if regex.search(node.get(attribute, '')):
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds', '')))
        if x2 > x1 and y2 > y1:
            found = (x1 // 2 if left else (x1 + x2) // 2, (y1 + y2) // 2)
            if option != 'last':
                break

if found is None:
    sys.exit(1)
print(*found)

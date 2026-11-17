import sys
# splice.py FILE START_MARKER FUNC_MARKER NEWFILE: replaces from START_MARKER to the end of the
# top-level function opened at FUNC_MARKER (first line that is exactly "}") with NEWFILE's text.
p, start, func, newp = sys.argv[1:5]
s = open(p, encoding="utf-8", newline="").read()
nl = "\r\n" if "\r\n" in s else "\n"
a = s.index(start)
b = s.index(func, a)
e = s.index(nl + "}" + nl, b) + len(nl + "}" + nl)
new = open(newp, encoding="utf-8").read().replace("\r\n", "\n").replace("\n", nl)
s = s[:a] + new + s[e:]
open(p, "w", encoding="utf-8", newline="").write(s)
print("ok", a, e)

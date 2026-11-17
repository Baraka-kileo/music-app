import sys
from PIL import Image
# join.py OUT IN1 IN2 ... -> images side by side
out, ins = sys.argv[1], sys.argv[2:]
ims = [Image.open(p) for p in ins]
h = max(i.height for i in ims)
w = sum(i.width for i in ims) + 6 * (len(ims) - 1)
c = Image.new("RGB", (w, h), (255, 255, 255))
x = 0
for i in ims:
    c.paste(i, (x, 0)); x += i.width + 6
c.save(out)

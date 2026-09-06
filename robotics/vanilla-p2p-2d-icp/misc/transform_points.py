import math
import random
import sys


source_path = sys.argv[1]
dx = float(sys.argv[2])
dy = float(sys.argv[3])
angle = float(sys.argv[4])
noise = float(sys.argv[5]) if len(sys.argv) > 5 else 0.0

c = math.cos(angle)
s = math.sin(angle)

with open(source_path) as source:
    for line in source:
        x, y = map(float, line.split(","))
        target_x = c * x - s * y + dx + random.gauss(0.0, noise)
        target_y = s * x + c * y + dy + random.gauss(0.0, noise)
        print(f"{target_x},{target_y}")

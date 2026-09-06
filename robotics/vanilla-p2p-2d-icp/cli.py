import sys
from icp import point_to_point_icp

## USAGE
## python3 test_cli.py source.txt target.txt

def load_points(path):
  with open(path) as file:
      return [tuple(map(float, line.split(","))) for line in file]

source = load_points(sys.argv[1])
target = load_points(sys.argv[2])
print(point_to_point_icp(source, target))

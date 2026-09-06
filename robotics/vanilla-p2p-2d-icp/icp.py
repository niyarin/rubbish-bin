import math
from typing import Sequence, Tuple

Point = Tuple[float, float]
Pose = Tuple[float, float, float]

def calc_centroid(points):
    return (sum(p[0] for p in points) / len(points),
            sum(p[1] for p in points) / len(points))

def estimate_angle(points, matched, centroid_points , centroid_matched):
    px,py = centroid_points
    qx,qy = centroid_matched

    dot = 0.0
    cross = 0.0
    for p, q in zip(points, matched):
        ax, ay = p[0] - px, p[1] - py
        bx, by = q[0] - qx, q[1] - qy
        dot += ax * bx + ay * by
        cross += ax * by - ay * bx

    return math.atan2(cross, dot)

def estimate_transform(points, matched):
    px,py = calc_centroid(points)
    qx,qy = calc_centroid(matched)

    angle = estimate_angle(points, matched, (px,py), (qx,qy))

    # calc translaction
    c, s = math.cos(angle), math.sin(angle)
    tx = qx - (c * px - s * py)
    ty = qy - (s * px + c * py)
    return tx,  ty, angle

def point_to_point_icp(
    source: Sequence[Point],
    target: Sequence[Point],
    max_iterations: int = 50,
    tolerance: float = 1e-6,
) -> Pose:
    points = [(float(x), float(y)) for x, y in source]
    targets = [(float(x), float(y)) for x, y in target]

    total_angle = 0.0
    total_tx = 0.0
    total_ty = 0.0

    for _ in range(max_iterations):
        matched = [
            min(targets, key=lambda q: (p[0] - q[0]) ** 2 + (p[1] - q[1]) ** 2)
            for p in points
        ]

        tx, ty, angle = estimate_transform(points, matched)

        c, s = math.cos(angle), math.sin(angle)
        # new points
        points = [(c * x - s * y + tx, s * x + c * y + ty) for x, y in points]

        # calc sum of the amount of translation
        total_tx, total_ty = (
            c * total_tx - s * total_ty + tx,
            s * total_tx + c * total_ty + ty,
        )
        total_angle = math.atan2(
            math.sin(total_angle + angle), math.cos(total_angle + angle)
        )

        if math.hypot(tx, ty) < tolerance and abs(angle) < tolerance:
            break

    return total_tx, total_ty, total_angle

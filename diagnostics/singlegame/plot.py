# /// script
# requires-python = ">=3.9"
# dependencies = ["matplotlib"]
# ///
"""dept-sequence-stats 스위프 결과 그래프 (uv 임시 실행).

사용법 (어디서 실행해도 됨, 스크립트 위치 기준 처리):
    uv run diagnostics/singlegame/plot.py

입력: singlegame/<버전>/response/*-response.json (스윕 스크립트 산출물)
출력: singlegame/<버전>/sweep.png (누적 게임 수[만] vs 측정 ms)
"""
import argparse
import json
import re
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

# API 버전. 결과 폴더(diagnostics/singlegame/<버전>/) 위치에 쓰인다.
# 버전이 바뀌면 여기만 수정하면 된다.
API_VERSION = "1"

BASE_DIR = Path(__file__).resolve().parent
RESULT_DIR = BASE_DIR / API_VERSION
RESPONSE_DIR = RESULT_DIR / "response"


def load_points() -> list[tuple[int, float]]:
    points: list[tuple[int, float]] = []
    for path in RESPONSE_DIR.glob("*-response.json"):
        m = re.fullmatch(r"(\d+)m-response\.json", path.name)
        if not m:
            continue
        with open(path, encoding="utf-8") as f:
            data = json.load(f)["data"]
        points.append((int(m.group(1)), float(data["ms"])))
    points.sort()
    return points


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=str(RESULT_DIR / "sweep.png"))
    args = parser.parse_args()

    points = load_points()
    if not points:
        raise SystemExit(f"측정 파일이 없습니다: {RESPONSE_DIR}/*-response.json")

    xs = [man for man, _ in points]
    ys = [ms for _, ms in points]

    fig, ax = plt.subplots(figsize=(10, 6))
    ax.plot(xs, ys, marker="o")
    for x, y in points:
        ax.annotate(f"{y:,.0f}", (x, y), textcoords="offset points", xytext=(0, 8),
                    ha="center", fontsize=8)
    ax.set_xlabel("Total games (x10k)")
    ax.set_ylabel("Time (ms)")
    ax.set_title("dept-sequence-stats (totalCourses=6, single dept, 1 run)")
    ax.grid(True, alpha=0.3)
    fig.tight_layout()
    fig.savefig(args.out, dpi=150)

    print(f"{'label':<8} {'ms':>12}")
    for man, ms in points:
        print(f"{man}m{'':<6} {ms:>12.2f}")
    print(f"저장: {args.out}")


if __name__ == "__main__":
    main()

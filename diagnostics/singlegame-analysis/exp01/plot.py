# /// script
# requires-python = ">=3.9"
# dependencies = ["matplotlib"]
# ///
"""exp01 plot.py — [EDITABLE] 전부. 이번 기준: 태그별 elapsed 평균 + min/max 오차막대.
다음 실험엔 기준ごと 갈아끼움. 데이터 출처: analyze.py의 rollup_tags.csv.
"""
import csv
from pathlib import Path

BASE = Path(__file__).resolve().parent
REPORT = BASE / "report"


def main():
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    tags, means, lo, hi = [], [], [], []
    with open(REPORT / "rollup_tags.csv", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            mean, mn, mx = float(row["elapsed_mean"]), float(row["elapsed_min"]), float(row["elapsed_max"])
            tags.append(f"{row['group']} (n={row['n']})")
            means.append(mean)
            lo.append(mean - mn)
            hi.append(mx - mean)
    fig, ax = plt.subplots()
    ax.errorbar(list(range(len(tags))), means, yerr=[lo, hi], marker="o", capsize=3)
    ax.set_xticks(list(range(len(tags))), tags, rotation=45)
    ax.set_ylabel("elapsed ms mean (http.server.request)")
    fig.tight_layout()
    fig.savefig(REPORT / "latency-breakdown.png")
    print(f"그림 저장: {REPORT / 'latency-breakdown.png'} ({len(tags)} groups)", flush=True)


if __name__ == "__main__":
    main()

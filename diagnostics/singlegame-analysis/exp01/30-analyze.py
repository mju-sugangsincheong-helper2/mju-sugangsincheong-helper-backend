# /// script
# requires-python = ">=3.9"
# dependencies = ["ijson", "matplotlib"]
# ///
"""30-analyze.py — raw/trace.json(20-dump.sh 산출)을 windows.tsv 구간만 잘라
trie → 행렬 txt + rollup.csv + png.

입력: raw/windows.tsv (label start end, date +%s.%N)
      raw/trace.json
출력: report/calltree-NNN-LABEL.txt (행렬 txt, 메트릭→method순)
      report/rollup.csv (중간재, png 재료, 1행=1라벨 루트 합산)
      report/latency-breakdown.png (최종)
값만 보여줌. 판단 없음. 용어는 JFR 원이름 그대로.
"""
import csv
import json
import re
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

BASE = Path(__file__).resolve().parent
RAW = BASE / "raw"
REPORT = BASE / "report"
JJSON = RAW / "trace.json"
WIN = RAW / "windows.tsv"

COLS = [
    "jdk.ExecutionSample",
    "jdk.NativeMethodSample",
    "jdk.SocketRead",
    "jdk.SocketWrite",
    "jdk.JavaMonitorWait",
    "jdk.JavaMonitorEnter",
    "jdk.ThreadPark",
    "jdk.ThreadSleep",
    "jdk.FileRead",
    "jdk.FileWrite",
    "jdk.JavaExceptionThrow",
]
COUNT_ONLY = {"jdk.ExecutionSample", "jdk.NativeMethodSample", "jdk.JavaExceptionThrow"}
# 표시용 allowlist (시간귀속은 전체스택 유지, 보여주기만 필터)
DISPLAY_PREFIXES = ("com.mjusugangsincheonghelper.", "jdk.proxy",
                    "org.springframework.")
WORKER_RE = re.compile(r"^http-nio-")
MARGIN = 0.2


def parse_jfr_time(s):
    s = re.sub(r"(\.\d{6})\d+", r"\1", s or "")
    if s.endswith("Z"):
        s = s[:-1] + "+00:00"
    try:
        return datetime.fromisoformat(s).timestamp()
    except ValueError:
        return None


def parse_dur(s):
    if not s:
        return 0.0
    m = re.fullmatch(r"PT(?:(\d+)H)?(?:(\d+)M)?([\d.]+)S", s)
    if not m:
        return 0.0
    return ((float(m.group(1) or 0) * 3600 + float(m.group(2) or 0) * 60
             + float(m.group(3))) * 1000.0)


def thread_of(ev):
    v = ev.get("values", {})
    for k in ("sampledThread", "eventThread"):
        t = v.get(k)
        if isinstance(t, dict) and t.get("javaName"):
            return t["javaName"]
    return ""


def frames_of(ev):
    v = ev.get("values", {})
    return (v.get("stackTrace") or {}).get("frames") or []


def mlabel(f):
    m = f.get("method", {})
    t = m.get("type", {})
    return sys.intern(f"{str(t.get('name', '?')).replace('/', '.')}.{m.get('name', '?')}")


def new_node(method):
    return {"method": method, "cnt": Counter(), "total": Counter(),
            "tmin": None, "tmax": None, "children": {}}


def main():
    REPORT.mkdir(parents=True, exist_ok=True)
    if not JJSON.exists():
        sys.exit(f"trace.json 없음: {JJSON} (20-dump.sh 먼저 실행)")
    if not WIN.exists():
        sys.exit(f"windows.tsv 없음: {WIN}")
    windows = []
    for line in WIN.read_text().splitlines():
        p = line.split()
        if len(p) < 3:
            continue
        windows.append((p[0], float(p[1]), float(p[2])))
    if not windows:
        sys.exit("windows.tsv 비어있음")

    # trace.json 스트리밍 파싱 (ijson 있으면 사용, 없으면 json.load)
    # 메모리 절약: 이벤트 dict 통째 보관이 아니라 slim tuple만 저장.
    try:
        import ijson  # type: ignore
        use_ijson = True
    except ImportError:
        use_ijson = False
    per_label = {label: [] for label, _, _ in windows}
    scanned = 0

    def slim(e):
        v = e.get("values", {})
        ts = parse_jfr_time(v.get("startTime", ""))
        if ts is None:
            return None
        for label, s, en in windows:
            if s - MARGIN <= ts <= en + MARGIN:
                break
        else:
            return None
        et = e.get("type", "")
        dur = parse_dur(v.get("duration")) if "duration" in v else 0.0
        thr = thread_of(e)
        fr = frames_of(e)
        if not fr:
            return label, (thr, et, ts, dur, None)
        r2l = [mlabel(f) for f in reversed(fr)]
        idx = next((i for i, m in enumerate(r2l) if "Controller." in m), None)
        if idx is None:
            return label, (thr, et, ts, dur, None)
        kept = [m for m in r2l[idx:]
                if m.startswith(DISPLAY_PREFIXES) or "$Proxy" in m]
        return label, (thr, et, ts, dur, kept or None)

    if use_ijson:
        with open(JJSON, "rb") as fh:
            for e in ijson.items(fh, "recording.events.item"):
                scanned += 1
                r = slim(e)
                if r:
                    per_label[r[0]].append(r[1])
    else:
        with open(JJSON, encoding="utf-8") as fh:
            doc = json.load(fh)
        for e in doc.get("recording", {}).get("events", []):
            scanned += 1
            r = slim(e)
            if r:
                per_label[r[0]].append(r[1])

    # 라벨별: 최다 http-nio 스레드 1개 → Controller 포함만 trie
    rollup_rows = []
    for label, s, en in windows:
        evs = per_label[label]
        elapsed = (en - s) * 1000.0
        thr_cnt = Counter(thr for thr, _, _, _, _ in evs if thr)
        workers = {t: c for t, c in thr_cnt.items() if WORKER_RE.match(t)}
        thread = max(workers, key=workers.get) if workers else ""
        g_cnt, g_tot = Counter(), Counter()
        root = new_node(None)
        if thread:
            for thr, et, ts, dur, kept in evs:
                if thr != thread:
                    continue
                g_cnt[et] += 1
                if et not in COUNT_ONLY:
                    g_tot[et] += dur
                if not kept:
                    continue
                path = kept
                end_ts = ts + (dur / 1000.0 if dur else 0.0)
                n = root
                for m in path:
                    n = n["children"].setdefault(m, new_node(m))
                    n["cnt"][et] += 1
                    if et not in COUNT_ONLY:
                        n["total"][et] += dur
                    n["tmin"] = ts if n["tmin"] is None else min(n["tmin"], ts)
                    n["tmax"] = end_ts if n["tmax"] is None else max(n["tmax"], end_ts)
        # 행렬 txt (전위순회, 깊이6, method 맨 뒤 들여쓰기)
        lines = []
        hdr = (["duration"] + [c for c in COLS for _ in ([0, 1] if c not in COUNT_ONLY
               else [0])] + ["method"])
        # 헤더 전개: cnt/total 분리 표기
        hdr2 = ["duration"]
        for c in COLS:
            if c in COUNT_ONLY:
                hdr2.append(c)
            else:
                hdr2 += [f"{c}-cnt", f"{c}-total"]
        hdr2.append("method")

        def span(n):
            if n["tmin"] is None:
                return 0.0
            return max(0.0, (n["tmax"] - n["tmin"]) * 1000.0)

        rows = []

        MAX_DEPTH = 256

        def walk(n, depth):
            if depth > MAX_DEPTH:
                if n["children"]:
                    rows.append((MAX_DEPTH, "  " * MAX_DEPTH + "...", None))
                return
            for key in sorted(n["children"]):
                k = n["children"][key]
                rows.append((depth, "  " * depth + (k["method"] or ""), k))
                walk(k, depth + 1)
        walk(root, 0)
        # 고정폭 계산
        str_rows = []
        for depth, mname, k in rows:
            if k is None:
                str_rows.append(([""] * (len(hdr2) - 1) + [mname]))
                continue
            cells = [f"{span(k):.1f}"]
            for c in COLS:
                if c in COUNT_ONLY:
                    cells.append(str(k["cnt"].get(c, 0)))
                else:
                    cells += [str(k["cnt"].get(c, 0)), f"{k['total'].get(c, 0.0):.1f}"]
            cells.append(mname)
            str_rows.append(cells)
        widths = [len(h) for h in hdr2]
        for r in str_rows:
            for i, c in enumerate(r):
                widths[i] = max(widths[i], len(c))
        out = [" ".join(h.ljust(w) if i == len(hdr2) - 1 else h.rjust(w)
                        for i, (h, w) in enumerate(zip(hdr2, widths)))]
        for r in str_rows:
            out.append(" ".join(c.ljust(w) if i == len(r) - 1 else c.rjust(w)
                                for i, (c, w) in enumerate(zip(r, widths))))
        if not rows:
            out.append("# (Controller 포함 스택 없음)")
        # 하단 요약: 부모와 숫자가 달라지는 지점만 뼈대로 (0 아닌 값만 표기)
        def sig(n):
            return tuple((n["cnt"].get(c, 0), round(n["total"].get(c, 0.0), 1))
                           for c in COLS)

        def short_method(m):
            m = re.sub(r"\$\$Lambda\.0x[0-9a-f]+", "$$Lambda", m)
            m = re.sub(r"\$\$SpringCGLIB\$\$0", "", m)
            cls, _, meth = m.rpartition(".")
            cls = cls.rsplit(".", 1)[-1]
            return f"{cls}.{meth}" if cls else meth

        def compact(n):
            parts = []
            for c in COLS:
                cnt = n["cnt"].get(c, 0)
                if not cnt:
                    continue
                short = c[4:] if c.startswith("jdk.") else c
                if c in COUNT_ONLY:
                    parts.append(f"{short}={cnt}")
                else:
                    parts.append(f"{short}={cnt}/{n['total'].get(c, 0.0):.1f}ms")
            return ", ".join(parts)
        summ = []

        def walk_summ(n, prefix, psig):
            kids = sorted(n["children"])
            for i, key in enumerate(kids):
                k = n["children"][key]
                s = sig(k)
                last = i == len(kids) - 1
                if s != psig:
                    br = "└─ " if last else "├─ "
                    summ.append((f"{prefix}{br}{short_method(k['method'])}",
                                 span(k), compact(k)))
                    walk_summ(k, prefix + ("   " if last else "│  "), s)
                else:
                    walk_summ(k, prefix, psig)
        walk_summ(root, "", None)
        if summ:
            mw = max(len(r[0]) for r in summ)
            out += ["", "# --- summary (숫자 바뀌는 지점만) ---"]
            for name, dur, met in summ:
                out.append(f"{name.ljust(mw)}  [{dur:>8.1f}ms]  {met}")
        (REPORT / f"calltree-{label}.txt").write_text(
            f"# label={label} elapsed={elapsed:.1f}ms thread={thread or '(없음)'}\n"
            + "\n".join(out) + "\n", encoding="utf-8")
        # rollup 중간재 1행
        row = {"label": label, "start": f"{s:.3f}", "end": f"{en:.3f}",
               "elapsed": f"{elapsed:.1f}", "thread": thread}
        for c in COLS:
            row[f"{c}#cnt"] = g_cnt.get(c, 0)
            if c not in COUNT_ONLY:
                row[f"{c}#total"] = f"{g_tot.get(c, 0.0):.1f}"
        rollup_rows.append(row)

    cols = ["label", "start", "end", "elapsed", "thread"]
    for c in COLS:
        cols.append(f"{c}#cnt")
        if c not in COUNT_ONLY:
            cols.append(f"{c}#total")
    with open(REPORT / "rollup.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=cols)
        w.writeheader()
        w.writerows(rollup_rows)

    # png는 windows.tsv만 사용 (JFR값 적층 없음)
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        labels = [label for label, _, _ in windows]
        el = [(en - s) * 1000.0 for _, s, en in windows]
        x = range(len(labels))
        fig, ax = plt.subplots()
        ax.plot(list(x), el, marker="o")
        ax.set_xticks(list(x), labels, rotation=45)
        ax.set_ylabel("elapsed ms (windows.tsv)")
        fig.tight_layout()
        fig.savefig(REPORT / "latency-breakdown.png")
    except ImportError:
        print("matplotlib 없음 → png 생략", flush=True)
    print(f"완료: scanned={scanned} labels={len(windows)}", flush=True)


if __name__ == "__main__":
    main()

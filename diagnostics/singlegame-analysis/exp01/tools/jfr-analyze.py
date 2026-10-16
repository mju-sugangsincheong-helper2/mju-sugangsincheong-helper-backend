# /// script
# requires-python = ">=3.9"
# dependencies = ["ijson"]
# ///
"""tools/jfr-analyze.py — 일반 JFR 분석 메커니즘. 수정 금지. 계약은 같은 exp의 tools/CONTRACT.md.

http.server.request 이벤트를 창(window)으로 삼아 Controller 앵커 trie를 쌓는다.
집계 단위: --group-by tag(기본, 동일 태그 합산) | window(창별 분리).
수치 판단·그림은 사례 측(plot.py)이 한다. 여기는 사실만 낸다.

입력: trace.json (http.server.request 포함)
출력: report/calltree-<tag>.txt       합산 trie 행렬 + summary
      report/rollup_windows.csv        1행=1창(1회 측정, 사실)
      report/rollup_tags.csv           1행=1태그(합산, mean/min/max는 기술값)
값만 보여줌. 판단 없음. 용어는 JFR 원이름 그대로.
"""
import argparse
import csv
import json
import re
import sys
from collections import Counter
from datetime import datetime
from pathlib import Path

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
WINDOW_TYPE = "http.server.request"
MARGIN = 0.05
INCLUDE_UNTAGGED = False


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
    # spans: {창idx: [tmin, tmax]} — 창별 커버리지 합산용. 합산 trie에서
    # 전역 tmin/tmax를 쓰면 trial 사이 sleep까지 들어가 부풀기 때문.
    return {"method": method, "cnt": Counter(), "total": Counter(),
            "spans": {}, "thr": Counter(), "children": {}}


def safe_tag(s):
    return re.sub(r"[^A-Za-z0-9._-]", "_", s or "untagged")


def iter_events(path, use_ijson):
    if use_ijson:
        import ijson  # type: ignore
        with open(path, "rb") as fh:
            for e in ijson.items(fh, "recording.events.item"):
                yield e
    else:
        with open(path, encoding="utf-8") as fh:
            doc = json.load(fh)
        for e in doc.get("recording", {}).get("events", []):
            yield e


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    ap.add_argument("--report-dir", required=True)
    ap.add_argument("--group-by", choices=["tag", "window"], default="tag")
    args = ap.parse_args()
    JJSON = Path(args.input)
    REPORT = Path(args.report_dir)
    REPORT.mkdir(parents=True, exist_ok=True)
    if not JJSON.exists():
        sys.exit(f"trace.json 없음: {JJSON}")
    try:
        import ijson  # type: ignore
        use_ijson = True
    except ImportError:
        use_ijson = False

    # pass 1: http.server.request → 창 목록
    windows = []
    scanned = 0
    for e in iter_events(JJSON, use_ijson):
        scanned += 1
        if e.get("type", "") != WINDOW_TYPE:
            continue
        v = e.get("values", {})
        tag = str(v.get("jfrTag", "") or "")
        if not tag and not INCLUDE_UNTAGGED:
            continue
        start = parse_jfr_time(v.get("startTime", ""))
        if start is None:
            continue
        dur = parse_dur(v.get("duration")) if "duration" in v else 0.0
        thr = thread_of(e)
        if not thr:
            continue
        windows.append({
            "tag": tag or "untagged",
            "route": str(v.get("route", "") or ""),
            "query": str(v.get("query", "") or ""),
            "method": str(v.get("method", "") or ""),
            "status": v.get("status", ""),
            "thread": thr,
            "start": start,
            "end": start + dur / 1000.0,
        })
    if not windows:
        sys.exit("http.server.request 창 없음 (JFR에 기록됐는지·태그 부여됐는지 확인)")
    windows.sort(key=lambda w: w["start"])
    WIN_THREADS = {w["thread"] for w in windows}

    # 그룹 키: tag(합산) | window(창별)
    if args.group_by == "tag":
        gkeys, glabel = [], {}
        for idx, w in enumerate(windows):
            if w["tag"] not in glabel:
                glabel[w["tag"]] = len(gkeys)
                gkeys.append(w["tag"])
        win_group = [glabel[w["tag"]] for w in windows]
    else:
        gkeys = [f"{w['tag']}#{i}" for i, w in enumerate(windows)]
        win_group = list(range(len(windows)))
    ngroups = len(gkeys)
    per_group = {g: [] for g in range(ngroups)}
    win_cnt = [Counter() for _ in windows]
    win_tot = [Counter() for _ in windows]

    # pass 2: 창에 속한 샘플 귀속 (스레드 + 시간 겹침)
    def slim(e):
        v = e.get("values", {})
        ts = parse_jfr_time(v.get("startTime", ""))
        if ts is None:
            return None
        et = e.get("type", "")
        if et == WINDOW_TYPE:
            return None
        if et not in COLS:
            return None  # ThreadStart 등은 수집만, trie 제외
        dur = parse_dur(v.get("duration")) if "duration" in v else 0.0
        thr = thread_of(e)
        if not thr or thr not in WIN_THREADS:
            return None
        fr = frames_of(e)
        kept = None
        if fr:
            # 표시 루트 = Controller 앵커. 컨테이너 프레임·백그라운드 제거.
            r2l = [mlabel(f) for f in reversed(fr)]
            idx = next((i for i, m in enumerate(r2l) if "Controller." in m), None)
            if idx is not None:
                kept = r2l[idx:]
        hits = []
        for i, w in enumerate(windows):
            # 귀속 = 창별 스레드 일치 + 시간 겹침 (전 창의 전 trial 포함)
            if thr == w["thread"] and w["start"] - MARGIN <= ts <= w["end"] + MARGIN:
                hits.append(i)
        if not hits:
            return None
        return hits, (thr, et, ts, dur, kept)

    for e in iter_events(JJSON, use_ijson):
        scanned += 1
        r = slim(e)
        if r:
            for i in r[0]:
                per_group[win_group[i]].append(r[1] + (i,))
                _, et, _, dur, _ = r[1]
                win_cnt[i][et] += 1
                if et not in COUNT_ONLY:
                    win_tot[i][et] += dur

    # 그룹별: trie (전체 스택, 필터 없음)
    tag_rows = []
    win_rows = []
    for g in range(ngroups):
        idxs = [i for i in range(len(windows)) if win_group[i] == g]
        w0 = windows[idxs[0]]
        evs = per_group[g]
        elapseds = [(windows[i]["end"] - windows[i]["start"]) * 1000.0 for i in idxs]
        elapsum = sum(elapseds)
        elapsed = elapsum / len(elapseds)
        emin, emax = min(elapseds), max(elapseds)
        threads = sorted({windows[i]["thread"] for i in idxs})
        g_cnt, g_tot = Counter(), Counter()
        root = new_node(None)
        for thr, et, ts, dur, kept, wi in evs:
            g_cnt[et] += 1
            if et not in COUNT_ONLY:
                g_tot[et] += dur
            if not kept:
                continue
            end_ts = ts + (dur / 1000.0 if dur else 0.0)
            # 창 밖에 걸친 대기 이벤트는 창 끝으로 자름 (귀속 커버리지는 창을 넘지 않음)
            wend = windows[wi]["end"]
            if end_ts > wend:
                end_ts = wend
            n = root
            for m in kept:
                # 키 = 메소드. trial들은 같은 요청 스택이므로 합산, 스레드는 분포로.
                n = n["children"].setdefault(m, new_node(m))
                n["cnt"][et] += 1
                if et not in COUNT_ONLY:
                    n["total"][et] += dur
                n["thr"][thr] += 1
                sp = n["spans"].get(wi)
                if sp is None:
                    n["spans"][wi] = [ts, end_ts]
                else:
                    if ts < sp[0]:
                        sp[0] = ts
                    if end_ts > sp[1]:
                        sp[1] = end_ts
        def span(n):
            tot = 0.0
            for a, b in n["spans"].values():
                tot += max(0.0, (b - a) * 1000.0)
            return tot

        rows = []
        MAX_DEPTH = 256

        hdr2 = ["duration"]
        for c in COLS:
            if c in COUNT_ONLY:
                hdr2.append(c)
            else:
                hdr2 += [f"{c}-cnt", f"{c}-total"]
        hdr2 += ["thread", "method"]

        def thr_str(n):
            return "{" + ", ".join(f"{t}:{c}" for t, c in n["thr"].most_common()) + "}"

        def walk(n, depth):
            if depth > MAX_DEPTH:
                if n["children"]:
                    rows.append((MAX_DEPTH, "  " * MAX_DEPTH + "...", "", None))
                return
            for key in sorted(n["children"]):
                k = n["children"][key]
                rows.append((depth, "  " * depth + (key or ""), thr_str(k), k))
                walk(k, depth + 1)
        walk(root, 0)
        str_rows = []
        for depth, mname, tname, k in rows:
            if k is None:
                str_rows.append(([""] * (len(hdr2) - 2) + [tname, mname]))
                continue
            cells = [f"{span(k):.1f}"]
            for c in COLS:
                if c in COUNT_ONLY:
                    cells.append(str(k["cnt"].get(c, 0)))
                else:
                    cells += [str(k["cnt"].get(c, 0)), f"{k['total'].get(c, 0.0):.1f}"]
            cells += [tname, mname]
            str_rows.append(cells)
        widths = [len(h) for h in hdr2]
        for r in str_rows:
            for j, c in enumerate(r):
                widths[j] = max(widths[j], len(c))
        # 뒤 2열(thread, method)은 좌측 정렬, 나머지는 우측 정렬
        out = [" ".join(h.ljust(wd) if j >= len(hdr2) - 2 else h.rjust(wd)
                        for j, (h, wd) in enumerate(zip(hdr2, widths)))]
        for r in str_rows:
            out.append(" ".join(c.ljust(wd) if j >= len(r) - 2 else c.rjust(wd)
                                for j, (c, wd) in enumerate(zip(r, widths))))
        if not rows:
            out.append("# (스택 없음)")

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
        # 요약 규칙: [(부모와 숫자 다름) AND (점유율>0%)] + 조상(문맥). 자식은 시간 내림차순.
        # 접기 없음. 자르는 건 0%뿐.
        def sig(n):
            return tuple((n["cnt"].get(c, 0), round(n["total"].get(c, 0.0), 1))
                           for c in COLS)

        def pct(n):
            return (span(n) / elapsum * 100.0) if elapsum else 0.0

        def located(n):
            return round(pct(n), 1) > 0

        def included(n, psig):
            if sig(n) != psig and located(n):
                return True
            return any(included(c, sig(n)) for c in n["children"].values())

        # 자체 시간: 자식 합과의 차. 샘플 1개는 정확히 한 자식으로만 가므로,
        # 전량이 자식으로 통하면 자체 0 = 순수 도관 = 접기 대상. 목록·설정 없음.
        def self_zero(n, kids):
            for c in COLS:
                if n["cnt"].get(c, 0) != sum(k["cnt"].get(c, 0) for k in kids):
                    return False
                if c not in COUNT_ONLY:
                    if round(n["total"].get(c, 0.0), 1) != round(
                            sum(k["total"].get(c, 0.0) for k in kids), 1):
                        return False
            return True

        summ = []

        def walk_summ(n, prefix, psig, depth):
            nsig = sig(n)
            kids = sorted((k for k in n["children"].values() if included(k, nsig)),
                          key=lambda k: span(k), reverse=True)
            for j, k in enumerate(kids):
                last = j == len(kids) - 1
                br = "└─ " if last else "├─ "
                # 한 줄 + 자체 0이면 접음. 시간 쓴 곳·분기점은 살림. 최상단은 유지.
                ck, folded = k, 0
                # depth: root=0. depth>=1부터 접기 (Controller 첫 줄 유지)
                if depth >= 1:
                    while True:
                        vc = [c for c in ck["children"].values()
                              if included(c, sig(ck))]
                        if len(vc) != 1 or not self_zero(ck, vc):
                            break
                        ck = vc[0]
                        folded += 1
                name = f"{prefix}{br}{short_method(ck['method'])}"
                if folded:
                    name += f" [+{folded}]"
                summ.append((name, span(ck), compact(ck), thr_str(ck)))
                walk_summ(ck, prefix + ("   " if last else "│  "), sig(ck), depth + 1)
        walk_summ(root, "", None, 0)
        if summ:
            mw = max(len(r[0]) for r in summ)
            out += ["", "# --- summary (sig-change + pct>0 + ancestors, hot-first) ---"]
            for name, dur, met, dstr in summ:
                pc = (dur / elapsum * 100.0) if elapsum else 0.0
                out.append(f"{name.ljust(mw)}  [{dur:>8.1f}ms {pc:>5.1f}%]  {met}  {dstr}")
        el_list = ";".join(f"{v:.1f}" for v in elapseds)
        (REPORT / f"calltree-{safe_tag(gkeys[g])}.txt").write_text(
            f"# group={gkeys[g]} n={len(idxs)} route={w0['method']} {w0['route']}?{w0['query']} "
            f"status={w0['status']} elapsed_sum={elapsum:.1f}ms mean={elapsed:.1f}ms min={emin:.1f}ms max={emax:.1f}ms "
            f"elapsed_all={el_list} threads={','.join(threads)}\n"
            + "\n".join(out) + "\n", encoding="utf-8")
        trow = {"group": gkeys[g], "n": len(idxs),
                "route": f"{w0['method']} {w0['route']}", "query": w0["query"],
                "status": w0["status"],
                "elapsed_mean": f"{elapsed:.1f}", "elapsed_min": f"{emin:.1f}",
                "elapsed_max": f"{emax:.1f}", "threads": ",".join(threads)}
        for c in COLS:
            trow[f"{c}#cnt"] = g_cnt.get(c, 0)
            if c not in COUNT_ONLY:
                trow[f"{c}#total"] = f"{g_tot.get(c, 0.0):.1f}"
        tag_rows.append(trow)
        for tnum, i in enumerate(sorted(idxs, key=lambda k: windows[k]["start"]), 1):
            w = windows[i]
            el = (w["end"] - w["start"]) * 1000.0
            wrow = {"group": gkeys[g], "trial": tnum, "route": f"{w['method']} {w['route']}",
                    "query": w["query"], "status": w["status"],
                    "elapsed": f"{el:.1f}", "thread": w["thread"]}
            for c in COLS:
                wrow[f"{c}#cnt"] = win_cnt[i].get(c, 0)
                if c not in COUNT_ONLY:
                    wrow[f"{c}#total"] = f"{win_tot[i].get(c, 0.0):.1f}"
            win_rows.append(wrow)

    mcols = ["group", "trial", "route", "query", "status", "elapsed", "thread"]
    for c in COLS:
        mcols.append(f"{c}#cnt")
        if c not in COUNT_ONLY:
            mcols.append(f"{c}#total")
    with open(REPORT / "rollup_windows.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=mcols)
        w.writeheader()
        w.writerows(win_rows)
    tcols = ["group", "n", "route", "query", "status",
             "elapsed_mean", "elapsed_min", "elapsed_max", "threads"]
    for c in COLS:
        tcols.append(f"{c}#cnt")
        if c not in COUNT_ONLY:
            tcols.append(f"{c}#total")
    with open(REPORT / "rollup_tags.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=tcols)
        w.writeheader()
        w.writerows(tag_rows)
    print(f"완료: scanned={scanned} windows={len(windows)} groups={ngroups}", flush=True)


if __name__ == "__main__":
    main()

"""Writes synthetic CTRF reports for timeline corner cases into the directory given as argv[1]."""
import json, random, sys, pathlib
out = pathlib.Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
T0 = 1792570616000

def report(tests, name):
    s = {k: 0 for k in ("passed", "failed", "skipped", "pending", "other")}
    for t in tests: s[t["status"]] += 1
    starts = [t["start"] for t in tests if t.get("start")] or [T0]
    stops = [t["stop"] for t in tests if t.get("stop")] or [T0]
    doc = {"reportFormat": "CTRF", "specVersion": "0.2.0", "results": {"tool": {"name": "synthetic"},
           "summary": {"tests": len(tests), **s, "start": min(starts), "stop": max(stops)}, "tests": tests}}
    (out / f"{name}.json").write_text(json.dumps(doc, ensure_ascii=False, indent=1))

def t(name, start, dur, status="passed", thread=None, **extra):
    d = {"name": name, "status": status, "duration": dur}
    if start is not None:
        d["start"] = T0 + start; d["stop"] = T0 + start + dur
    if thread is not None: d["threadId"] = thread
    d.update(extra); return d

# 1. small e2e run: 3 workers, a 0ms init failure, a retried flaky test, a skipped test
report([
  t("Login with valid credentials", 0, 4200, thread="worker-1"),
  t("Login retry", 6450, 1500, thread="worker-1", flaky=True, retries=1,
    retryAttempts=[{"attempt": 1, "status": "failed", "duration": 2100, "start": T0 + 4300, "stop": T0 + 6400, "message": "timeout"}]),
  t("Checkout: pay with card", 8000, 14000, thread="worker-1"),
  t("Search returns results", 22100, 3100, thread="worker-1"),
  t("Signup flow", 0, 9800, thread="worker-2"),
  t("Init fails fast", 9900, 0, "failed", thread="worker-2", message="BeforeAll failed"),
  t("Profile page loads", 9950, 5200, thread="worker-2"),
  t("Upload avatar", 15300, 7700, "failed", thread="worker-2"),
  t("Cart persists after reload", 0, 1200, thread="worker-3"),
  t("Cart badge count", 1300, 800, thread="worker-3"),
  t("Long running report export", 2200, 26000, thread="worker-3"),
  t("Admin dashboard", None, 0, "skipped", thread="worker-3"),
], "01-e2e-small")

# 2. dense: 8 threads, ~2.2k tests, 1% failures, occasional gaps
random.seed(7); tests = []
for w in range(1, 9):
    cur = random.randint(0, 400)
    while cur < 280000:
        d = int(random.expovariate(1/900)) + 5
        tests.append(t(f"pool-1-thread-{w} test {len(tests)}", cur, d, "failed" if random.random() < 0.01 else "passed", thread=f"pool-1-thread-{w}"))
        cur += d + int(random.expovariate(1/40))
        if random.random() < 0.01: cur += random.randint(3000, 12000)
report(tests, "02-dense-8-threads")

# 3. no thread IDs, jest-like 4 workers
random.seed(3); tests = []; ends = [0, 0, 0, 0]
for i in range(160):
    w = min(range(4), key=lambda k: ends[k]); d = random.randint(50, 4000)
    tests.append(t(f"file{i % 37}.test.ts > case {i}", ends[w] + random.randint(0, 30), d, "failed" if i % 41 == 0 else "passed"))
    ends[w] = tests[-1]["stop"] - T0
report(tests, "03-no-thread-ids")

# 4. two JVM forks that both call their thread "Test worker"
tests = []
for fork in range(2):
    cur = fork * 37
    for i in range(30):
        d = 200 + (i * 97 + fork * 31) % 1500
        tests.append(t(f"fork{fork} Test{i}", cur, d, thread="Test worker")); cur += d + 3
report(tests, "04-forks-same-thread-name")

# 5. zero span: everything 0ms at the same instant, one failure
report([t(f"instant {i}", 0, 0, "failed" if i == 3 else "passed", thread="main") for i in range(6)], "05-zero-span")

# 6. single test
report([t("the only test", 0, 1234, thread="main")], "06-single")

# 7. huge: 20k tests on 16 threads, 1% failures, retries
random.seed(11); tests = []
for w in range(16):
    cur = 0
    for i in range(1250):
        d = int(random.expovariate(1/150)) + 1
        st = "failed" if random.random() < 0.01 else "passed"
        extra = {}
        if random.random() < 0.005:
            extra = {"retries": 1, "flaky": True, "retryAttempts": [{"attempt": 1, "status": "failed", "duration": d, "start": T0 + cur, "stop": T0 + cur + d}]}
            cur += d + 2
        tests.append(t(f"suite {w} test {i}", cur, d, st, thread=f"ForkJoinPool-1-worker-{w + 1}", **extra)); cur += d + random.randint(0, 5)
report(tests, "07-huge-20k")

# 8. hostile names and thread names
names = ["section should collapse", "title renders", "gantt chart click", "click me", "2024-01-01 report", "%% not a comment", "5% load test",
         "dateFormat x", "Excludes weekends", "a: b; c #1; d #amp; e", "```fence``` breaker", "multi\nline\r\nname", "emoji 🚀 test ✅",
         "测试用例名称很长很长很长", "שלום עולם RTL", "<b>bold</b> & <script>x</script>", "tab\there", "%%{init: {'theme':'forest'}}%%", "accTitle: hijack", "x" * 300, "renders &#10; as a line break", "a&#58b &copy", "constructor"]
report([t(n, i * 1000, 5000, "failed" if i % 5 == 0 else "passed", thread=["worker #1; x", "worker: 2", "<main>", "`tick`", "constructor", "1", "__proto__"][i % 7]) for i, n in enumerate(names)], "08-hostile-names")

# 9. long run: 2h, 3 threads, long tests
report([t(f"long {w}-{i}", i * 600000 + w * 1000, 550000 + w * 10000, "failed" if (w, i) == (1, 5) else "passed", thread=f"w{w}") for w in range(3) for i in range(12)], "09-long-2h")

# 10. very short: 400ms total, 2 threads
report([t(f"fast {w}-{i}", i * 40 + w * 5, 35, "failed" if (w, i) == (0, 4) else "passed", thread=f"w{w}") for w in range(2) for i in range(10)], "10-very-short")

# 11. more than 24h span (absolute clock)
report([t("day one", 0, 60000, thread="a"), t("day three", 50 * 3600000, 60000, "failed", thread="a")], "11-over-24h")

# 12. 100 unique thread ids, sequential -> re-pack into 1 lane
report([t(f"seq {i}", i * 1000, 900, thread=f"thread-{i}") for i in range(100)], "12-too-many-threads")

# 13. 60 fully overlapping tests without thread ids -> 40 lanes + hidden
report([t(f"overlap {i}", i, 10000) for i in range(60)], "13-too-many-lanes")

# 14. untimed and invalid data mixed with good data
report([t("good", 0, 1000, thread="a"), t("no timing", None, 500, thread="a"),
        {"name": "stop before start", "status": "passed", "duration": None, "start": T0 + 5000, "stop": T0 + 4000, "threadId": "a"},
        {"name": "start only", "status": "passed", "duration": 700, "start": T0 + 2000, "threadId": "a"},
        {"name": "stop only", "status": "failed", "duration": 300, "stop": T0 + 4000, "threadId": "a"},
        t("pending", None, 0, "pending")], "14-partial-data")
# 15. three shards, then "re-run failed jobs" runs shard 3 again two hours later
tests = []
for shard in (1, 2, 3):
    cur = 0
    for i in range(20):
        name = "checkout fails" if (shard, i) == (3, 7) else f"shard {shard} test {i}"
        tests.append(t(name, cur, 1400 + 37 * i, "failed" if (shard, i) == (3, 7) else "passed", thread=f"shard-{shard}")); cur += 1400 + 37 * i + 20
cur = 2 * 3600000
for i in range(20):
    name = "checkout fails" if i == 7 else f"shard 3 test {i}"
    tests.append(t(name, cur, 1400 + 37 * i, thread="shard-3")); cur += 1400 + 37 * i + 20
report(tests, "15-rerun-shard")

# 16. go-ctrf-json-reporter after "go test -count=2": the final result spans both
# attempts and repeats the last one
report([
    t("TestFlaky", 0, 9000, retries=1, retryAttempts=[
        {"attempt": 1, "status": "failed", "duration": 2000, "start": T0, "stop": T0 + 2000},
        {"attempt": 2, "status": "passed", "duration": 3000, "start": T0 + 6000, "stop": T0 + 9000}]),
    t("TestOther", 2000, 4000),
    t("TestParse", 9000, 3000, "failed"),
    t("TestParse/empty", 9000, 1000),
    t("TestParse/broken", 10000, 2000, "failed"),
], "16-go-count2")
print("ok")

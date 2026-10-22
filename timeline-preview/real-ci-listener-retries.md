### Timeline

**25** tests (and **9** runs before a retry) on **4** threads in **9** lanes and **1** more lane in **4.8s** · busy **47%** of the time · up to **8** at once<br>Slowest: `secondLongTwoSecondTest()` (2.0s on `ForkJoinPool-1-worker-2`) · Last to finish: `Flaky test passed on the second run` at 0:04.784 on `ForkJoinPool-1-worker-1`

```mermaid
---
config:
  gantt:
    displayMode: compact
    useWidth: 1000
    leftPadding: 212
    rightPadding: 40
  themeCSS: ".taskText.critText0,.taskText.critText1,.taskText.critText2,.taskText.critText3{fill:#ffffff !important}.active0,.active1,.active2,.active3{stroke-dasharray:4 2}"
  themeVariables:
    taskBkgColor: '#4ac26b'
    taskBorderColor: '#1a7f37'
    critBkgColor: '#cf222e'
    critBorderColor: '#82071e'
    activeTaskBkgColor: '#eac54f'
    activeTaskBorderColor: '#9a6700'
    doneTaskBkgColor: '#afb8c1'
    doneTaskBorderColor: '#6e7781'
    taskTextColor: '#1f2328'
    taskTextDarkColor: '#1f2328'
    sectionBkgColor: '#8c959f'
    sectionBkgColor2: '#8c959f'
    vertLineColor: '#8c959f'
---
gantt
accTitle: Test execution timeline
accDescr: 25 tests and 9 runs before a retry on 4 threads in 9 lanes and 1 more lane over 4.8s, 7 failed. The slowest test is secondLongTwoSecondTest() (2.0s on ForkJoinPool-1-worker-2). The last to finish is Flaky test passed on the second run on ForkJoinPool-1-worker-1, at 0:04.784.
dateFormat D HH:mm:ss.SSS
axisFormat %-S.%Ls
todayMarker off
section ​ForkJoinPool-1-worker-1
​ :16 00:00:00.000, 0ms
​secondLongOneSecondT… :16 00:00:00.058, 1014ms
​1 :16 00:00:01.073, 504ms
​2 :crit, 16 00:00:04.144, 117ms
​Flaky test… :16 00:00:04.261, 523ms
​ :16 00:00:04.784, 0ms
section ​ForkJoinPool-1-worker-1 (2)
​ :16 00:00:00.102, 77ms
​ :16 00:00:00.180, 10ms
​Flaky test… :active, 16 00:00:00.234, 523ms
section ​ForkJoinPool-1-worker-2
​3 :active, 16 00:00:00.000, 162ms
​ :16 00:00:00.181, 65ms
​ :16 00:00:00.264, 22ms
​4 :active, 16 00:00:00.331, 116ms
​secondLongTwoSecondTest() :16 00:00:00.759, 2004ms
​5 :crit, 16 00:00:04.146, 109ms
section ​ForkJoinPool-1-worker-2 (2)
​6 :active, 16 00:00:00.000, 176ms
​7 :crit, 16 00:00:04.184, 183ms
​8 :crit, 16 00:00:04.371, 108ms
section ​ForkJoinPool-1-worker-2 (3)
​9 :16 00:00:00.039, 104ms
​ :16 00:00:04.228, 128ms
section ​ForkJoinPool-1-worker-3
​ :done, 16 00:00:00.056, 93ms
​ :active, 16 00:00:00.154, 46ms
section ​ForkJoinPool-1-worker-3 (2)
​ :16 00:00:00.101, 89ms
​ :16 00:00:00.191, 112ms
​ :16 00:00:00.364, 18ms
​firstLongTwoSecondTest() :16 00:00:00.449, 2004ms
​ :crit, 16 00:00:04.214, 143ms
​ :crit, 16 00:00:04.357, 17ms
section ​ForkJoinPool-1-worker-4
​firstLongH… :16 00:00:00.223, 516ms
​firstLongOneSecondTest… :16 00:00:00.739, 1005ms
​ :crit, 16 00:00:04.263, 132ms
section ​ForkJoinPool-1-worker-4 (2)
​ :active, 16 00:00:00.191, 139ms
​ :16 00:00:00.359, 24ms
section ​Runs before a retry
​ :active, 16 00:00:00.025, 118ms
​ :active, 16 00:00:00.173, 35ms
```

<sub>🟩 passed · 🟥 failed · 🟨 failed, then retried (dashed) · ⬜ skipped or other · 0:00 = 2026-10-22 20:48:30.942 UTC</sub>
<br><sub>6 skipped or pending tests are not shown.</sub>
<br><sub>Tests on `ForkJoinPool-1-worker-1`, `ForkJoinPool-1-worker-2`, `ForkJoinPool-1-worker-3` and 1 other thread overlap in time (processes sharing a thread name, or a test timed around others), so they are shown as several lanes.</sub>
<br><sub>2 runs before a retry overlap other tests on their thread (such runs do not record where they ran), so they are shown in a lane of their own.</sub>
<br><sub>A number on a bar is the row with that number (#) in the test timings below.</sub>
<br><sub>Tests too short to tell apart are merged into one bar.</sub>

| Thread | Tests | Runs before a retry | Failed | Busy | Idle | Utilization |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: |
| ForkJoinPool-1-worker-1 | 4 | 0 | 1 | 2.2s | 2.6s | 45% |
| ForkJoinPool-1-worker-1 (2) | 2 | 1 | 0 | 610ms | 4.2s | 13% |
| ForkJoinPool-1-worker-2 | 5 | 2 | 1 | 2.5s | 2.3s | 52% |
| ForkJoinPool-1-worker-2 (2) | 2 | 1 | 2 | 467ms | 4.3s | 10% |
| ForkJoinPool-1-worker-2 (3) | 1 | 1 | 0 | 232ms | 4.6s | 5% |
| ForkJoinPool-1-worker-3 | 1 | 1 | 0 | 139ms | 4.6s | 3% |
| ForkJoinPool-1-worker-3 (2) | 6 | 0 | 2 | 2.4s | 2.4s | 50% |
| ForkJoinPool-1-worker-4 | 3 | 0 | 1 | 1.7s | 3.1s | 35% |
| ForkJoinPool-1-worker-4 (2) | 1 | 1 | 0 | 163ms | 4.6s | 3% |
| Runs before a retry | 0 | 2 | 0 | 153ms | 4.6s | 3% |

<details>
<summary>Test timings (34)</summary>

| # | Start | Duration | Thread | Test | Status |
| ---: | ---: | ---: | :--- | :--- | :--- |
|  | 0:00.058 | 1.0s | ForkJoinPool-1-worker-1 | secondLongOneSecondTest() | ✅ passed |
| 1 | 0:01.073 | 504ms | ForkJoinPool-1-worker-1 | secondLongHalfSecondTest() | ✅ passed |
| 2 | 0:04.144 | 117ms | ForkJoinPool-1-worker-1 | initializationError | ❌ failed |
|  | 0:04.260 | 524ms | ForkJoinPool-1-worker-1 | Flaky test passed on the second run | ✅ passed |
|  | 0:00.102 | 77ms | ForkJoinPool-1-worker-1 (2) | secondTestTwo() | ✅ passed |
|  | 0:00.180 | 10ms | ForkJoinPool-1-worker-1 (2) | secondTestOne() | ✅ passed |
|  | 0:00.234 | 523ms | ForkJoinPool-1-worker-1 (2) | Flaky test passed on the second run (attempt 1) | ❌ failed |
| 3 | 0:00.000 | 162ms | ForkJoinPool-1-worker-2 | initializationError (attempt 1) | ❌ failed |
|  | 0:00.181 | 46ms | ForkJoinPool-1-worker-2 | firstTestTwo() | ✅ passed |
|  | 0:00.227 | 19ms | ForkJoinPool-1-worker-2 | firstTestOne() | ✅ passed |
|  | 0:00.264 | 22ms | ForkJoinPool-1-worker-2 | refundsTheFee() | ✅ passed |
| 4 | 0:00.331 | 116ms | ForkJoinPool-1-worker-2 | Second failed test (attempt 1) | ❌ failed |
|  | 0:00.759 | 2.0s | ForkJoinPool-1-worker-2 | secondLongTwoSecondTest() | ✅ passed |
| 5 | 0:04.146 | 109ms | ForkJoinPool-1-worker-2 | initializationError | ❌ failed |
| 6 | 0:00.000 | 176ms | ForkJoinPool-1-worker-2 (2) | teardownError (attempt 1) | ❌ failed |
| 7 | 0:04.184 | 183ms | ForkJoinPool-1-worker-2 (2) | teardownError | ❌ failed |
| 8 | 0:04.371 | 108ms | ForkJoinPool-1-worker-2 (2) | Second failed test | ❌ failed |
| 9 | 0:00.039 | 104ms | ForkJoinPool-1-worker-2 (3) | testBeforeFailingTeardown() (attempt 1) | ✅ passed |
|  | 0:04.228 | 128ms | ForkJoinPool-1-worker-2 (3) | testBeforeFailingTeardown() | ✅ passed |
|  | 0:00.056 | 93ms | ForkJoinPool-1-worker-3 | requiresUnavailableService() | ⏭️ skipped |
|  | 0:00.154 | 46ms | ForkJoinPool-1-worker-3 | factory() (attempt 1) | ❌ failed |
|  | 0:00.101 | 89ms | ForkJoinPool-1-worker-3 (2) | secondSuccessTest() | ✅ passed |
|  | 0:00.191 | 112ms | ForkJoinPool-1-worker-3 (2) | firstSuccessTest(TestReporter) | ✅ passed |
|  | 0:00.364 | 18ms | ForkJoinPool-1-worker-3 (2) | refundsEachAmount(int) \[1\] 1 | ✅ passed |
|  | 0:00.449 | 2.0s | ForkJoinPool-1-worker-3 (2) | firstLongTwoSecondTest() | ✅ passed |
|  | 0:04.214 | 143ms | ForkJoinPool-1-worker-3 (2) | usesData(int) | ❌ failed |
|  | 0:04.360 | 9ms | ForkJoinPool-1-worker-3 (2) | factory() | ❌ failed |
|  | 0:00.223 | 516ms | ForkJoinPool-1-worker-4 | firstLongHalfSecondTest() | ✅ passed |
|  | 0:00.739 | 1.0s | ForkJoinPool-1-worker-4 | firstLongOneSecondTest() | ✅ passed |
|  | 0:04.263 | 132ms | ForkJoinPool-1-worker-4 | firstFailedTest() | ❌ failed |
|  | 0:00.191 | 139ms | ForkJoinPool-1-worker-4 (2) | firstFailedTest() (attempt 1) | ❌ failed |
|  | 0:00.359 | 24ms | ForkJoinPool-1-worker-4 (2) | refundsEachAmount(int) \[2\] 2 | ✅ passed |
|  | 0:00.025 | 118ms | Runs before a retry | usesData(int) (attempt 1) | ❌ failed |
|  | 0:00.173 | 35ms | Runs before a retry | initializationError (attempt 1) | ❌ failed |

</details>

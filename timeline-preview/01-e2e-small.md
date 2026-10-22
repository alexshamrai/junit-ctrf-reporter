### Timeline

**11** tests (and **1** run before a retry) on **3** threads in **28.2s** · busy **89%** of the time · up to **3** at once<br>Slowest and last to finish: `Long running report export` (26.0s on `worker-3`, until 0:28.200)

```mermaid
---
config:
  gantt:
    displayMode: compact
    useWidth: 1000
    leftPadding: 79
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
accDescr: 11 tests and 1 run before a retry on 3 threads over 28.2s, 2 failed. The slowest test is Long running report export (26.0s on worker-3). The last to finish is Long running report export on worker-3, at 0:28.200.
dateFormat D HH:mm:ss.SSS
axisFormat %-M:%S
todayMarker off
section ​worker-1
​ :16 00:00:00.000, 0ms
​Login with valid cr… :16 00:00:00.000, 4200ms
​1 :active, 16 00:00:04.300, 2100ms
​2 :16 00:00:06.450, 1500ms
​Checkout∶ pay with card :16 00:00:08.000, 14000ms
​Search return… :16 00:00:22.100, 3100ms
​ :16 00:00:28.200, 0ms
section ​worker-2
​Signup flow :16 00:00:00.000, 9516ms
​ :crit, milestone, 16 00:00:09.900, 0ms
​Profile page loads :16 00:00:10.284, 4866ms
​Upload avatar :crit, 16 00:00:15.300, 7700ms
section ​worker-3
​3 :16 00:00:00.000, 1200ms
​4 :16 00:00:01.300, 800ms
​Long running report export :16 00:00:02.200, 26000ms
```

<sub>🟩 passed · 🟥 failed · 🟨 failed, then retried (dashed) · ♦️ failed too fast for a bar · 0:00 = 2026-10-21 08:16:56.000 UTC</sub>
<br><sub>1 skipped or pending test is not shown.</sub>
<br><sub>A number on a bar is the row with that number (#) in the test timings below.</sub>

| Thread | Tests | Runs before a retry | Failed | Busy | Idle | Utilization |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: |
| worker-1 | 4 | 1 | 0 | 24.9s | 3.3s | 88% |
| worker-2 | 4 | 0 | 2 | 22.7s | 5.5s | 80% |
| worker-3 | 3 | 0 | 0 | 28.0s | 200ms | 99% |

<details>
<summary>Test timings (12)</summary>

| # | Start | Duration | Thread | Test | Status |
| ---: | ---: | ---: | :--- | :--- | :--- |
|  | 0:00.000 | 4.2s | worker-1 | Login with valid credentials | ✅ passed |
| 1 | 0:04.300 | 2.1s | worker-1 | Login retry (attempt 1) | ❌ failed |
| 2 | 0:06.450 | 1.5s | worker-1 | Login retry | ✅ passed |
|  | 0:08.000 | 14.0s | worker-1 | Checkout: pay with card | ✅ passed |
|  | 0:22.100 | 3.1s | worker-1 | Search returns results | ✅ passed |
|  | 0:00.000 | 9.8s | worker-2 | Signup flow | ✅ passed |
|  | 0:09.900 | 0ms | worker-2 | Init fails fast | ❌ failed |
|  | 0:09.950 | 5.2s | worker-2 | Profile page loads | ✅ passed |
|  | 0:15.300 | 7.7s | worker-2 | Upload avatar | ❌ failed |
| 3 | 0:00.000 | 1.2s | worker-3 | Cart persists after reload | ✅ passed |
| 4 | 0:01.300 | 800ms | worker-3 | Cart badge count | ✅ passed |
|  | 0:02.200 | 26.0s | worker-3 | Long running report export | ✅ passed |

</details>

### Timeline

**3** tests on **1** thread in **4.0s** · busy **50%** of the time<br>Slowest: `good` (1.0s on `a`) · Last to finish: `stop only` at 0:04.000 on `a`

```mermaid
---
config:
  gantt:
    displayMode: compact
    useWidth: 1000
    leftPadding: 75
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
accDescr: 3 tests on 1 thread over 4.0s, 1 failed. The slowest test is good (1.0s on a). The last to finish is stop only on a, at 0:04.000.
dateFormat D HH:mm:ss.SSS
axisFormat %-S.%Ls
todayMarker off
section ​a
​ :16 00:00:00.000, 0ms
​good :16 00:00:00.000, 1000ms
​start only :16 00:00:02.000, 700ms
​stop only :crit, 16 00:00:03.700, 300ms
​ :16 00:00:04.000, 0ms
```

<sub>🟩 passed · 🟥 failed · 0:00 = 2026-10-21 08:16:56.000 UTC</sub>
<br><sub>1 test without start and stop times is not shown.</sub>
<br><sub>1 test whose start and stop times cannot be placed (stopping before starting, or not a plausible date) is not shown.</sub>
<br><sub>1 skipped or pending test is not shown.</sub>

<details>
<summary>Test timings (3)</summary>

| Start | Duration | Thread | Test | Status |
| ---: | ---: | :--- | :--- | :--- |
| 0:00.000 | 1.0s | a | good | ✅ passed |
| 0:02.000 | 700ms | a | start only | ✅ passed |
| 0:03.700 | 300ms | a | stop only | ❌ failed |

</details>

### Timeline

**2** tests on **1** thread in **2m** · busy **100%** of the time<br>Slowest: `day one` (1m on `a`) · Last to finish: `day three` at 50:01:00.000 on `a`

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
accDescr: 2 tests on 1 thread over 2m, 1 failed. The slowest test is day one (1m on a). The last to finish is day three on a, at 50:01:00.000.
dateFormat D HH:mm:ss.SSS
axisFormat %-M:%S
todayMarker off
section ​a
​ :16 00:00:00.000, 0ms
​day one :16 00:00:00.000, 60000ms
​day three :crit, 16 00:01:03.600, 60000ms
​ :16 00:02:03.600, 0ms
​ :vert, 16 00:01:01.800, 0ms
```

<sub>🟩 passed · 🟥 failed · 0:00 = 2026-10-21 08:16:56.000 UTC</sub>
<br><sub>No test ran for 49h 59m, so that stretch is cut out of the chart at the gray line; the tables show real times.</sub>

<details>
<summary>Test timings (2)</summary>

| Start | Duration | Thread | Test | Status |
| ---: | ---: | :--- | :--- | :--- |
| 0:00:00.000 | 1m | a | day one | ✅ passed |
| 50:00:00.000 | 1m | a | day three | ❌ failed |

</details>

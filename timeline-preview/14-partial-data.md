### Timeline

**3** tests on **1** thread in **4.0s** · busy **50%** of the time

```mermaid
---
config:
  gantt:
    displayMode: compact
    useWidth: 1000
    leftPadding: 75
    rightPadding: 40
  themeVariables:
    taskBkgColor: '#4ac26b'
    taskBorderColor: '#1a7f37'
    critBkgColor: '#f85149'
    critBorderColor: '#cf222e'
    activeTaskBkgColor: '#eac54f'
    activeTaskBorderColor: '#9a6700'
    doneTaskBkgColor: '#afb8c1'
    doneTaskBorderColor: '#6e7781'
    taskTextColor: '#1f2328'
    taskTextDarkColor: '#1f2328'
    sectionBkgColor: '#8c959f'
    sectionBkgColor2: '#8c959f'
---
gantt
accTitle: Test execution timeline
accDescr: 3 tests on 1 thread over 4.0s, 1 failed
dateFormat D HH:mm:ss.SSS
axisFormat %-S.%Ls
todayMarker off
section ​a
​good :16 00:00:00.000, 1000ms
​start only :16 00:00:02.000, 700ms
​stop only :crit, 16 00:00:03.700, 300ms
```

<sub>🟩 passed · 🟥 failed · 0:00 = 2026-10-21 08:16:56 UTC</sub>
<br><sub>1 test without start and stop times is not shown.</sub>
<br><sub>1 test with a stop time before the start time is not shown.</sub>
<br><sub>1 skipped or pending test is not shown.</sub>

<details>
<summary>Test timings (3)</summary>

| Start | Duration | Thread | Test | Status |
| ---: | ---: | :--- | :--- | :--- |
| 0:00.000 | 1.0s | a | good | ✅ passed |
| 0:02.000 | 700ms | a | start only | ✅ passed |
| 0:03.700 | 300ms | a | stop only | ❌ failed |

</details>

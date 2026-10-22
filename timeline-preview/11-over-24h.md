### Timeline

**1** test on **1** thread in **1m** · busy **100%** of the time

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
accDescr: 1 test on 1 thread over 1m, 1 failed
dateFormat D HH:mm:ss.SSS
axisFormat %-M:%S
todayMarker off
section ​a
​day three :crit, 16 00:00:00.000, 60000ms
```

<sub>🟥 failed · 0:00 = 2026-10-23 10:16:56 UTC</sub>
<br><sub>1 test from another run in the same report is not shown: they ran 49h 59m or more before or after these.</sub>

<details>
<summary>Test timings (1)</summary>

| Start | Duration | Thread | Test | Status |
| ---: | ---: | :--- | :--- | :--- |
| 0:00.000 | 1m | a | day three | ❌ failed |

</details>

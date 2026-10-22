### Timeline

**1** test on **1** thread in **1.2s** · busy **100%** of the time

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
accDescr: 1 test on 1 thread over 1.2s, 0 failed. The slowest test is the only test (1.2s on main). The last to finish is the only test on main, at 0:01.234.
dateFormat D HH:mm:ss.SSS
axisFormat %-S.%Ls
todayMarker off
section ​main
​ :16 00:00:00.000, 0ms
​the only test :16 00:00:00.000, 1234ms
​ :16 00:00:01.234, 0ms
```

<sub>🟩 passed · 0:00 = 2026-10-21 08:16:56.000 UTC</sub>

<details>
<summary>Test timings (1)</summary>

| Start | Duration | Thread | Test | Status |
| ---: | ---: | :--- | :--- | :--- |
| 0:00.000 | 1.2s | main | the only test | ✅ passed |

</details>

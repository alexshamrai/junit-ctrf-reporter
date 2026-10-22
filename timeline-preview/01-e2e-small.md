### Timeline

**11** tests and **1** earlier attempt on **3** threads in **28.2s** · busy **89%** of the time · up to **3** at once

```mermaid
---
config:
  gantt:
    displayMode: compact
    useWidth: 1000
    leftPadding: 78
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
accDescr: 11 tests on 3 threads over 28.2s, 2 failed
dateFormat D HH:mm:ss.SSS
axisFormat %-M:%S
todayMarker off
section ​worker-1
​Login with valid cre… :16 00:00:00.000, 4200ms
​ :active, 16 00:00:04.300, 2100ms
​ :16 00:00:06.450, 1500ms
​Checkout∶ pay with card :16 00:00:08.000, 14000ms
​Search returns… :16 00:00:22.100, 3100ms
section ​worker-2
​Signup flow :16 00:00:00.000, 9516ms
​ :crit, milestone, 16 00:00:09.900, 0ms
​Profile page loads :16 00:00:10.284, 4866ms
​Upload avatar :crit, 16 00:00:15.300, 7700ms
section ​worker-3
​ :16 00:00:00.000, 1200ms
​ :16 00:00:01.300, 800ms
​Long running report export :16 00:00:02.200, 26000ms
```

<sub>🟩 passed · 🟥 failed · 🟨 failed, then retried · ♦️ failed too fast for a bar · 0:00 = 2026-10-21 08:16:56 UTC</sub>
<br><sub>1 skipped or pending test is not shown.</sub>

| Thread | Tests | Earlier attempts | Failed | Busy | Idle | Utilization |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: |
| worker-1 | 4 | 1 | 0 | 24.9s | 3.3s | 88% |
| worker-2 | 4 | 0 | 2 | 22.7s | 5.5s | 80% |
| worker-3 | 3 | 0 | 0 | 28.0s | 200ms | 99% |

<details>
<summary>Test timings (12)</summary>

| Start | Duration | Thread | Test | Status |
| ---: | ---: | :--- | :--- | :--- |
| 0:00.000 | 4.2s | worker-1 | Login with valid credentials | ✅ passed |
| 0:00.000 | 9.8s | worker-2 | Signup flow | ✅ passed |
| 0:00.000 | 1.2s | worker-3 | Cart persists after reload | ✅ passed |
| 0:01.300 | 800ms | worker-3 | Cart badge count | ✅ passed |
| 0:02.200 | 26.0s | worker-3 | Long running report export | ✅ passed |
| 0:04.300 | 2.1s | worker-1 | Login retry (attempt 1) | ❌ failed |
| 0:06.450 | 1.5s | worker-1 | Login retry | ✅ passed |
| 0:08.000 | 14.0s | worker-1 | Checkout: pay with card | ✅ passed |
| 0:09.900 | 0ms | worker-2 | Init fails fast | ❌ failed |
| 0:09.950 | 5.2s | worker-2 | Profile page loads | ✅ passed |
| 0:15.300 | 7.7s | worker-2 | Upload avatar | ❌ failed |
| 0:22.100 | 3.1s | worker-1 | Search returns results | ✅ passed |

</details>

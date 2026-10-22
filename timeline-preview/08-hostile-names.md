### Timeline

**23** tests on **7** threads in **27.0s** · busy **61%** of the time · up to **5** at once<br>Slowest: `5% load test` (5.0s on `__proto__`) · Last to finish: `constructor` at 0:27.000 on `worker: 2`

```mermaid
---
config:
  gantt:
    displayMode: compact
    useWidth: 1000
    leftPadding: 104
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
accDescr: 23 tests on 7 threads over 27.0s, 5 failed. The slowest test is 5％ load test (5.0s on __proto__). The last to finish is constructor on worker: 2, at 0:27.000.
dateFormat D HH:mm:ss.SSS
axisFormat %-M:%S
todayMarker off
section ​__proto__
​ :16 00:00:00.000, 0ms
​5％ load test :16 00:00:06.000, 5000ms
​测试用例名称很长很长很长 :16 00:00:13.000, 5000ms
​renders ＆#10, as a lin… :crit, 16 00:00:20.000, 5000ms
​ :16 00:00:27.000, 0ms
section ​'tick'
​click me :16 00:00:03.000, 5000ms
​'''fence''' breaker :crit, 16 00:00:10.000, 5000ms
​％％{init∶ {'theme'∶… :16 00:00:17.000, 5000ms
section ​<main>
​gantt chart click :16 00:00:02.000, 5000ms
​a∶ b, c #1, d #amp, e :16 00:00:09.000, 5000ms
​tab here :16 00:00:16.000, 5000ms
section ​1
​％％ not a comment :crit, 16 00:00:05.000, 5000ms
​emoji 🚀 test ✅ :16 00:00:12.000, 5000ms
​xxxxxxxxxxxxxxxxxxxxx… :16 00:00:19.000, 5000ms
section ​constructor
​2024-01-01 report :16 00:00:04.000, 5000ms
​multi line name :16 00:00:11.000, 5000ms
​accTitle∶ hijack :16 00:00:18.000, 5000ms
section ​worker #1, x
​section should collapse :crit, 16 00:00:00.000, 5000ms
​dateFormat x :16 00:00:07.000, 5000ms
​שלום עולם RTL :16 00:00:14.000, 5000ms
​a＆#58b ＆copy :16 00:00:21.000, 5000ms
section ​worker∶ 2
​title renders :16 00:00:01.000, 5000ms
​Excludes weekends :16 00:00:08.000, 5000ms
​<b>bold</b> ＆ <scrip… :crit, 16 00:00:15.000, 5000ms
​constructor :16 00:00:22.000, 5000ms
```

<sub>🟩 passed · 🟥 failed · 0:00 = 2026-10-21 08:16:56.000 UTC</sub>

| Thread | Tests | Failed | Busy | Idle | Utilization |
| :--- | ---: | ---: | ---: | ---: | ---: |
| \_\_proto\_\_ | 3 | 1 | 15.0s | 12.0s | 56% |
| \`tick\` | 3 | 1 | 15.0s | 12.0s | 56% |
| &lt;main&gt; | 3 | 0 | 15.0s | 12.0s | 56% |
| 1 | 3 | 1 | 15.0s | 12.0s | 56% |
| constructor | 3 | 0 | 15.0s | 12.0s | 56% |
| worker #⁠1; x | 4 | 1 | 20.0s | 7.0s | 74% |
| worker: 2 | 4 | 1 | 20.0s | 7.0s | 74% |

<details>
<summary>Test timings (23)</summary>

| Start | Duration | Thread | Test | Status |
| ---: | ---: | :--- | :--- | :--- |
| 0:06.000 | 5.0s | \_\_proto\_\_ | 5% load test | ✅ passed |
| 0:13.000 | 5.0s | \_\_proto\_\_ | 测试用例名称很长很长很长 | ✅ passed |
| 0:20.000 | 5.0s | \_\_proto\_\_ | renders &amp;#⁠10; as a line break | ❌ failed |
| 0:03.000 | 5.0s | \`tick\` | click me | ✅ passed |
| 0:10.000 | 5.0s | \`tick\` | \`\`\`fence\`\`\` breaker | ❌ failed |
| 0:17.000 | 5.0s | \`tick\` | %%{init: {'theme':'forest'}}%% | ✅ passed |
| 0:02.000 | 5.0s | &lt;main&gt; | gantt chart click | ✅ passed |
| 0:09.000 | 5.0s | &lt;main&gt; | a: b; c #⁠1; d #⁠amp; e | ✅ passed |
| 0:16.000 | 5.0s | &lt;main&gt; | tab here | ✅ passed |
| 0:05.000 | 5.0s | 1 | %% not a comment | ❌ failed |
| 0:12.000 | 5.0s | 1 | emoji 🚀 test ✅ | ✅ passed |
| 0:19.000 | 5.0s | 1 | xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx…xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx | ✅ passed |
| 0:04.000 | 5.0s | constructor | 2024-01-01 report | ✅ passed |
| 0:11.000 | 5.0s | constructor | multi line name | ✅ passed |
| 0:18.000 | 5.0s | constructor | accTitle: hijack | ✅ passed |
| 0:00.000 | 5.0s | worker #⁠1; x | section should collapse | ❌ failed |
| 0:07.000 | 5.0s | worker #⁠1; x | dateFormat x | ✅ passed |
| 0:14.000 | 5.0s | worker #⁠1; x | שלום עולם RTL | ✅ passed |
| 0:21.000 | 5.0s | worker #⁠1; x | a&amp;#⁠58b &amp;copy | ✅ passed |
| 0:01.000 | 5.0s | worker: 2 | title renders | ✅ passed |
| 0:08.000 | 5.0s | worker: 2 | Excludes weekends | ✅ passed |
| 0:15.000 | 5.0s | worker: 2 | &lt;b&gt;bold&lt;/b&gt; &amp; &lt;script&gt;x&lt;/script&gt; | ❌ failed |
| 0:22.000 | 5.0s | worker: 2 | constructor | ✅ passed |

</details>

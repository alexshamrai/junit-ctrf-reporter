### Timeline

**20000** tests and **103** earlier attempts on **16** threads in **3m 16s** · busy **95%** of the time · up to **16** at once<br>Slowest: `suite 1 test 667` (1.5s on `ForkJoinPool-1-worker-2`) · Last to finish: `suite 7 test 1249` at 3:16.193 on `ForkJoinPool-1-worker-8`

```mermaid
---
config:
  gantt:
    displayMode: compact
    useWidth: 1000
    leftPadding: 194
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
accDescr: 20000 tests and 103 earlier attempts on 16 threads over 3m 16s, 186 failed. The slowest test is suite 1 test 667 (1.5s on ForkJoinPool-1-worker-2). The last to finish is suite 7 test 1249 on ForkJoinPool-1-worker-8, at 3:16.193.
dateFormat D HH:mm:ss.SSS
axisFormat %-M:%S
todayMarker off
section ​ForkJoinPool-1-worker-1
​ :16 00:00:00.000, 0ms
​198 tests :16 00:00:00.000, 28099ms
​ :crit, 16 00:00:28.099, 769ms
​212 tests :16 00:00:28.868, 29678ms
​ :crit, 16 00:00:58.546, 769ms
​ :16 00:00:59.315, 7531ms
​ :crit, 16 00:01:06.846, 768ms
​ :16 00:01:07.614, 2843ms
​ :active, 16 00:01:10.457, 768ms
​ :16 00:01:11.225, 4266ms
​ :crit, 16 00:01:15.491, 769ms
​ :16 00:01:16.260, 6087ms
​ :active, 16 00:01:22.347, 768ms
​ :16 00:01:23.115, 3309ms
​ :crit, 16 00:01:26.424, 768ms
​ :16 00:01:27.192, 6016ms
​ :crit, 16 00:01:33.208, 769ms
​ :16 00:01:33.977, 7963ms
​ :crit, 16 00:01:41.940, 768ms
​150 tests :16 00:01:42.708, 21718ms
​ :crit, 16 00:02:04.426, 768ms
​ :16 00:02:05.194, 7406ms
​ :active, 16 00:02:12.600, 769ms
​ :16 00:02:13.369, 3979ms
​ :crit, 16 00:02:17.348, 769ms
​ :16 00:02:18.117, 5430ms
​ :crit, 16 00:02:23.547, 768ms
​ :16 00:02:24.315, 3622ms
​ :crit, 16 00:02:27.937, 769ms
​ :16 00:02:28.706, 9549ms
​ :active, 16 00:02:38.255, 768ms
​ :16 00:02:39.023, 4462ms
​ :crit, 16 00:02:43.485, 768ms
​99 tests :16 00:02:44.253, 16437ms
​ :crit, 16 00:03:00.690, 769ms
​ :16 00:03:01.460, 8551ms
​ :16 00:03:16.193, 0ms
section ​ForkJoinPool-1-worker-2
​ :16 00:00:00.000, 4208ms
​ :active, 16 00:00:04.208, 768ms
​ :16 00:00:04.976, 488ms
​ :crit, 16 00:00:05.464, 769ms
​456 tests :16 00:00:06.233, 63768ms
​ :crit, 16 00:01:10.001, 768ms
​ :16 00:01:10.769, 1675ms
​ :crit, 16 00:01:12.444, 768ms
​ :16 00:01:13.212, 3329ms
​ :crit, 16 00:01:16.541, 769ms
​ :16 00:01:17.310, 1621ms
​ :crit, 16 00:01:18.931, 769ms
​92 tests :16 00:01:19.700, 14909ms
​ :crit, 16 00:01:34.609, 768ms
​ :16 00:01:35.377, 13230ms
​ :active, 16 00:01:48.607, 768ms
​89 tests :16 00:01:49.375, 16112ms
​ :crit, 16 00:02:05.487, 768ms
​ :16 00:02:06.255, 207ms
​ :crit, 16 00:02:06.462, 768ms
​ :16 00:02:07.230, 5512ms
​ :crit, 16 00:02:12.744, 768ms
​147 tests :16 00:02:13.513, 25831ms
​ :active, 16 00:02:39.344, 768ms
​203 tests :16 00:02:40.112, 29051ms
section ​ForkJoinPool-1-worker-3
​ :16 00:00:00.000, 15995ms
​ :active, 16 00:00:15.995, 769ms
​ :16 00:00:16.764, 7487ms
​ :crit, 16 00:00:24.251, 769ms
​ :16 00:00:25.020, 1502ms
​ :crit, 16 00:00:26.522, 768ms
​ :16 00:00:27.290, 11599ms
​ :active, 16 00:00:38.889, 768ms
​ :16 00:00:39.657, 7062ms
​ :crit, 16 00:00:46.719, 769ms
​ :16 00:00:47.488, 2831ms
​ :crit, 16 00:00:50.319, 768ms
​ :16 00:00:51.087, 600ms
​ :crit, 16 00:00:51.687, 768ms
​ :16 00:00:52.455, 10495ms
​ :active, 16 00:01:02.950, 768ms
​ :16 00:01:03.718, 5742ms
​ :active, 16 00:01:09.460, 768ms
​ :16 00:01:10.228, 6738ms
​ :active, 16 00:01:16.966, 769ms
​ :16 00:01:17.735, 3157ms
​ :crit, 16 00:01:20.892, 768ms
​ :16 00:01:21.660, 5004ms
​ :active, 16 00:01:26.664, 768ms
​ :16 00:01:27.432, 3619ms
​ :active, 16 00:01:31.051, 768ms
​249 tests :16 00:01:31.819, 36093ms
​ :active, 16 00:02:07.912, 769ms
​ :16 00:02:08.681, 107ms
​ :active, 16 00:02:08.788, 768ms
​ :16 00:02:09.556, 4049ms
​ :active, 16 00:02:13.605, 769ms
​ :16 00:02:14.374, 1817ms
​ :crit, 16 00:02:16.191, 768ms
​ :16 00:02:16.959, 9829ms
​ :active, 16 00:02:26.788, 768ms
​ :16 00:02:27.556, 535ms
​ :crit, 16 00:02:28.091, 768ms
​ :16 00:02:28.859, 4287ms
​ :crit, 16 00:02:33.146, 769ms
​ :16 00:02:33.915, 10658ms
​ :active, 16 00:02:44.573, 769ms
​ :16 00:02:45.342, 8261ms
​ :active, 16 00:02:53.603, 769ms
​ :16 00:02:54.372, 11105ms
section ​ForkJoinPool-1-worker-4
​ :16 00:00:00.000, 7166ms
​ :crit, 16 00:00:07.166, 768ms
​ :16 00:00:07.934, 2085ms
​ :crit, 16 00:00:10.019, 768ms
​ :16 00:00:10.787, 1243ms
​ :crit, 16 00:00:12.030, 769ms
​ :16 00:00:12.799, 2821ms
​ :crit, 16 00:00:15.620, 769ms
​ :16 00:00:16.389, 6614ms
​ :active, 16 00:00:23.003, 769ms
​ :16 00:00:23.772, 6812ms
​ :crit, 16 00:00:30.584, 768ms
​ :16 00:00:31.353, 7862ms
​ :active, 16 00:00:39.215, 768ms
​ :16 00:00:39.983, 514ms
​ :crit, 16 00:00:40.497, 769ms
​ :16 00:00:41.266, 2226ms
​ :crit, 16 00:00:43.492, 768ms
​97 tests :16 00:00:44.260, 15095ms
​ :active, 16 00:00:59.355, 769ms
​ :16 00:01:00.124, 2155ms
​ :crit, 16 00:01:02.279, 769ms
​ :16 00:01:03.048, 5030ms
​ :active, 16 00:01:08.078, 769ms
​ :16 00:01:08.847, 12473ms
​ :crit, 16 00:01:21.320, 769ms
​129 tests :16 00:01:22.089, 18655ms
​ :active, 16 00:01:40.744, 768ms
​99 tests :16 00:01:41.512, 14377ms
​ :crit, 16 00:01:55.889, 768ms
​158 tests :16 00:01:56.657, 22265ms
​ :crit, 16 00:02:18.922, 769ms
​231 tests :16 00:02:19.691, 35927ms
​ :crit, 16 00:02:55.618, 768ms
​ :16 00:02:56.386, 9871ms
section ​ForkJoinPool-1-worker-5
​ :16 00:00:00.000, 6329ms
​ :crit, 16 00:00:06.329, 769ms
​ :16 00:00:07.098, 1943ms
​ :crit, 16 00:00:09.041, 769ms
​ :16 00:00:09.810, 3509ms
​ :crit, 16 00:00:13.319, 768ms
​ :16 00:00:14.087, 9427ms
​ :crit, 16 00:00:23.514, 769ms
​100 tests :16 00:00:24.283, 17285ms
​ :active, 16 00:00:41.568, 768ms
​ :16 00:00:42.336, 11024ms
​ :crit, 16 00:00:53.360, 769ms
​152 tests :16 00:00:54.129, 22944ms
​ :crit, 16 00:01:17.073, 768ms
​89 tests :16 00:01:17.841, 15120ms
​ :crit, 16 00:01:32.961, 769ms
​ :16 00:01:33.730, 7696ms
​ :active, 16 00:01:41.426, 769ms
​ :16 00:01:42.195, 6028ms
​ :crit, 16 00:01:48.223, 768ms
​440 tests :16 00:01:48.991, 61711ms
​ :crit, 16 00:02:50.702, 768ms
​ :16 00:02:51.470, 249ms
​ :crit, 16 00:02:51.719, 769ms
​ :16 00:02:52.488, 13259ms
​ :crit, 16 00:03:05.747, 768ms
​ :16 00:03:06.515, 7840ms
section ​ForkJoinPool-1-worker-6
​ :16 00:00:00.000, 9001ms
​ :active, 16 00:00:09.001, 769ms
​ :16 00:00:09.770, 6097ms
​ :crit, 16 00:00:15.867, 768ms
​ :16 00:00:16.635, 4356ms
​ :crit, 16 00:00:20.991, 769ms
​ :16 00:00:21.760, 2020ms
​ :crit, 16 00:00:23.780, 769ms
​141 tests :16 00:00:24.549, 21674ms
​ :active, 16 00:00:46.223, 768ms
​ :16 00:00:46.991, 637ms
​ :active, 16 00:00:47.628, 768ms
​ :16 00:00:48.396, 9540ms
​ :crit, 16 00:00:57.936, 769ms
​105 tests :16 00:00:58.705, 17262ms
​ :active, 16 00:01:15.967, 768ms
​ :16 00:01:16.735, 3342ms
​ :crit, 16 00:01:20.077, 769ms
​239 tests :16 00:01:20.846, 33421ms
​ :active, 16 00:01:54.267, 769ms
​150 tests :16 00:01:55.036, 22672ms
​ :crit, 16 00:02:17.708, 768ms
​ :16 00:02:18.476, 2982ms
​ :crit, 16 00:02:21.458, 769ms
​ :16 00:02:22.227, 4892ms
​ :crit, 16 00:02:27.119, 768ms
​ :16 00:02:27.887, 3406ms
​ :crit, 16 00:02:31.293, 769ms
​135 tests :16 00:02:32.062, 20681ms
​ :active, 16 00:02:52.743, 769ms
​ :16 00:02:53.512, 3047ms
​ :active, 16 00:02:56.559, 768ms
​ :16 00:02:57.327, 2082ms
​ :crit, 16 00:02:59.409, 769ms
​ :16 00:03:00.178, 10672ms
​ :crit, 16 00:03:10.850, 768ms
​ :16 00:03:11.618, 3158ms
section ​ForkJoinPool-1-worker-7
​ :16 00:00:00.000, 6333ms
​ :crit, 16 00:00:06.333, 769ms
​96 tests :16 00:00:07.102, 16193ms
​ :active, 16 00:00:23.295, 768ms
​ :16 00:00:24.063, 1406ms
​ :active, 16 00:00:25.469, 768ms
​ :16 00:00:26.237, 2410ms
​ :active, 16 00:00:28.647, 769ms
​ :16 00:00:29.416, 9939ms
​ :crit, 16 00:00:39.355, 769ms
​ :16 00:00:40.124, 9116ms
​ :active, 16 00:00:49.240, 769ms
​ :16 00:00:50.009, 950ms
​ :active, 16 00:00:50.959, 769ms
​ :16 00:00:51.728, 6167ms
​ :crit, 16 00:00:57.895, 769ms
​93 tests :16 00:00:58.664, 15866ms
​ :crit, 16 00:01:14.530, 768ms
​ :16 00:01:15.298, 2304ms
​ :crit, 16 00:01:17.602, 769ms
​211 tests :16 00:01:18.371, 31102ms
​ :crit, 16 00:01:49.473, 769ms
​ :16 00:01:50.242, 4713ms
​ :crit, 16 00:01:54.955, 768ms
​ :16 00:01:55.723, 14010ms
​ :crit, 16 00:02:09.733, 768ms
​89 tests :16 00:02:10.501, 14629ms
​ :crit, 16 00:02:25.130, 768ms
​ :16 00:02:25.898, 10080ms
​ :active, 16 00:02:35.978, 769ms
​194 tests :16 00:02:36.747, 28970ms
section ​ForkJoinPool-1-worker-8
​307 tests :16 00:00:00.000, 49539ms
​ :active, 16 00:00:49.539, 768ms
​ :16 00:00:50.307, 10634ms
​ :crit, 16 00:01:00.941, 769ms
​ :16 00:01:01.710, 4177ms
​ :crit, 16 00:01:05.887, 768ms
​ :16 00:01:06.655, 2343ms
​ :crit, 16 00:01:08.998, 768ms
​111 tests :16 00:01:09.766, 16922ms
​ :active, 16 00:01:26.688, 768ms
​ :16 00:01:27.456, 14715ms
​ :active, 16 00:01:42.171, 769ms
​ :16 00:01:42.940, 461ms
​ :active, 16 00:01:43.401, 768ms
​ :16 00:01:44.169, 8079ms
​ :crit, 16 00:01:52.248, 769ms
​ :16 00:01:53.017, 78ms
​ :active, 16 00:01:53.095, 768ms
​ :16 00:01:53.863, 7416ms
​ :crit, 16 00:02:01.279, 768ms
​ :16 00:02:02.047, 6106ms
​ :crit, 16 00:02:08.153, 769ms
​109 tests :16 00:02:08.922, 18156ms
​ :crit, 16 00:02:27.078, 769ms
​ :16 00:02:27.847, 10636ms
​ :crit, 16 00:02:38.483, 768ms
​ :16 00:02:39.251, 10006ms
​ :crit, 16 00:02:49.257, 769ms
​ :16 00:02:50.026, 1890ms
​ :crit, 16 00:02:51.916, 768ms
​ :16 00:02:52.684, 5627ms
​ :crit, 16 00:02:58.311, 768ms
​118 tests :16 00:02:59.079, 17114ms
section ​ForkJoinPool-1-worker-9
​99 tests :16 00:00:00.000, 14909ms
​ :crit, 16 00:00:14.909, 768ms
​ :16 00:00:15.677, 3818ms
​ :crit, 16 00:00:19.495, 768ms
​ :16 00:00:20.263, 7207ms
​ :active, 16 00:00:27.470, 769ms
​194 tests :16 00:00:28.239, 28804ms
​ :active, 16 00:00:57.043, 769ms
​ :16 00:00:57.812, 6621ms
​ :crit, 16 00:01:04.433, 768ms
​114 tests :16 00:01:05.201, 19047ms
​ :active, 16 00:01:24.248, 769ms
​ :16 00:01:25.017, 937ms
​ :active, 16 00:01:25.954, 768ms
​ :16 00:01:26.722, 2068ms
​ :active, 16 00:01:28.790, 769ms
​ :16 00:01:29.559, 13766ms
​ :crit, 16 00:01:43.325, 768ms
​ :16 00:01:44.093, 13052ms
​ :crit, 16 00:01:57.145, 769ms
​ :16 00:01:57.914, 3962ms
​ :active, 16 00:02:01.876, 769ms
​ :16 00:02:02.645, 11235ms
​ :crit, 16 00:02:13.880, 768ms
​ :16 00:02:14.648, 6637ms
​ :crit, 16 00:02:21.285, 768ms
​191 tests :16 00:02:22.053, 28417ms
​ :crit, 16 00:02:50.470, 768ms
​ :16 00:02:51.238, 2796ms
​ :active, 16 00:02:54.034, 768ms
​ :16 00:02:54.802, 218ms
​ :crit, 16 00:02:55.020, 768ms
​ :16 00:02:55.788, 6197ms
​ :crit, 16 00:03:01.985, 768ms
​ :16 00:03:02.753, 11174ms
section ​ForkJoinPool-1-worker-10
​191 tests :16 00:00:00.000, 31807ms
​ :crit, 16 00:00:31.807, 768ms
​ :16 00:00:32.575, 3848ms
​ :crit, 16 00:00:36.423, 769ms
​ :16 00:00:37.192, 13228ms
​ :crit, 16 00:00:50.420, 768ms
​123 tests :16 00:00:51.188, 18989ms
​ :active, 16 00:01:10.177, 769ms
​ :16 00:01:10.946, 4226ms
​ :active, 16 00:01:15.172, 768ms
​ :16 00:01:15.940, 6034ms
​ :crit, 16 00:01:21.974, 769ms
​110 tests :16 00:01:22.743, 17142ms
​ :active, 16 00:01:39.885, 768ms
​ :16 00:01:40.653, 7877ms
​ :crit, 16 00:01:48.530, 769ms
​ :16 00:01:49.299, 12352ms
​ :crit, 16 00:02:01.651, 768ms
​ :16 00:02:02.419, 422ms
​ :crit, 16 00:02:02.841, 769ms
​ :16 00:02:03.610, 2000ms
​ :crit, 16 00:02:05.610, 768ms
​ :16 00:02:06.378, 4972ms
​ :crit, 16 00:02:11.350, 768ms
​281 tests :16 00:02:12.118, 42741ms
​ :crit, 16 00:02:54.859, 769ms
​ :16 00:02:55.628, 5680ms
​ :crit, 16 00:03:01.308, 768ms
​ :16 00:03:02.076, 8918ms
section ​ForkJoinPool-1-worker-11
​ :16 00:00:00.000, 9152ms
​ :crit, 16 00:00:09.152, 769ms
​ :16 00:00:09.921, 5722ms
​ :crit, 16 00:00:15.643, 768ms
​ :16 00:00:16.411, 2067ms
​ :crit, 16 00:00:18.478, 768ms
​279 tests :16 00:00:19.246, 42316ms
​ :active, 16 00:01:01.562, 768ms
​ :16 00:01:02.330, 10451ms
​ :crit, 16 00:01:12.781, 769ms
​117 tests :16 00:01:13.550, 18766ms
​ :active, 16 00:01:32.316, 768ms
​161 tests :16 00:01:33.084, 23120ms
​ :crit, 16 00:01:56.204, 769ms
​ :16 00:01:56.973, 1141ms
​ :crit, 16 00:01:58.114, 768ms
​97 tests :16 00:01:58.882, 14708ms
​ :crit, 16 00:02:13.590, 768ms
​ :16 00:02:14.358, 3775ms
​ :crit, 16 00:02:18.133, 769ms
​ :16 00:02:18.902, 1762ms
​ :crit, 16 00:02:20.664, 769ms
​213 tests :16 00:02:21.433, 32126ms
​ :active, 16 00:02:53.559, 769ms
​ :16 00:02:54.328, 8797ms
​ :active, 16 00:03:03.125, 768ms
​ :16 00:03:03.893, 4622ms
​ :crit, 16 00:03:08.515, 768ms
​ :16 00:03:09.283, 3465ms
section ​ForkJoinPool-1-worker-12
​ :16 00:00:00.000, 3597ms
​ :crit, 16 00:00:03.597, 768ms
​ :crit, 16 00:00:04.365, 660ms
​ :16 00:00:05.025, 6894ms
​ :active, 16 00:00:11.919, 769ms
​ :16 00:00:12.688, 15226ms
​ :active, 16 00:00:27.914, 769ms
​93 tests :16 00:00:28.683, 16798ms
​ :crit, 16 00:00:45.481, 768ms
​ :16 00:00:46.249, 6842ms
​ :crit, 16 00:00:53.091, 769ms
​ :16 00:00:53.860, 3655ms
​ :crit, 16 00:00:57.515, 769ms
​ :16 00:00:58.284, 6222ms
​ :crit, 16 00:01:04.506, 768ms
​ :16 00:01:05.274, 1156ms
​ :crit, 16 00:01:06.430, 769ms
​ :16 00:01:07.199, 11302ms
​ :active, 16 00:01:18.501, 769ms
​121 tests :16 00:01:19.270, 17432ms
​ :active, 16 00:01:36.702, 769ms
​ :16 00:01:37.471, 2646ms
​ :active, 16 00:01:40.117, 768ms
​ :16 00:01:40.887, 1056ms
​ :active, 16 00:01:41.943, 769ms
​ :16 00:01:42.712, 4209ms
​ :crit, 16 00:01:46.921, 769ms
​ :16 00:01:47.690, 2487ms
​ :active, 16 00:01:50.177, 768ms
​ :16 00:01:50.945, 6272ms
​ :crit, 16 00:01:57.217, 768ms
​ :16 00:01:57.985, 5479ms
​ :active, 16 00:02:03.464, 769ms
​ :16 00:02:04.233, 7060ms
​ :crit, 16 00:02:11.293, 768ms
​157 tests :16 00:02:12.061, 28316ms
​ :active, 16 00:02:40.377, 768ms
​ :16 00:02:41.145, 6205ms
​ :active, 16 00:02:47.350, 768ms
​ :16 00:02:48.118, 491ms
​ :crit, 16 00:02:48.609, 769ms
​ :16 00:02:49.378, 9499ms
​ :crit, 16 00:02:58.877, 769ms
​ :16 00:02:59.646, 6928ms
​ :crit, 16 00:03:06.574, 769ms
​ :16 00:03:07.343, 8163ms
section ​ForkJoinPool-1-worker-13
​ :16 00:00:00.000, 13556ms
​ :crit, 16 00:00:13.556, 769ms
​ :16 00:00:14.325, 3040ms
​ :crit, 16 00:00:17.365, 768ms
​226 tests :16 00:00:18.133, 32654ms
​ :active, 16 00:00:50.787, 769ms
​ :16 00:00:51.556, 12042ms
​ :crit, 16 00:01:03.598, 768ms
​ :16 00:01:04.366, 3827ms
​ :crit, 16 00:01:08.193, 769ms
​ :16 00:01:08.962, 12798ms
​ :crit, 16 00:01:21.760, 769ms
​ :16 00:01:22.529, 3801ms
​ :crit, 16 00:01:26.330, 768ms
​ :16 00:01:27.098, 2664ms
​ :crit, 16 00:01:29.762, 769ms
​ :16 00:01:30.531, 1448ms
​ :active, 16 00:01:31.979, 768ms
​ :16 00:01:32.747, 4338ms
​ :crit, 16 00:01:37.085, 769ms
​ :16 00:01:37.854, 1723ms
​ :crit, 16 00:01:39.577, 769ms
​ :16 00:01:40.346, 905ms
​ :crit, 16 00:01:41.251, 769ms
​ :16 00:01:42.020, 4611ms
​ :crit, 16 00:01:46.631, 768ms
​ :16 00:01:47.399, 2190ms
​ :crit, 16 00:01:49.589, 768ms
​ :16 00:01:50.357, 805ms
​ :crit, 16 00:01:51.162, 768ms
​ :16 00:01:51.930, 2263ms
​ :active, 16 00:01:54.193, 769ms
​ :16 00:01:54.962, 226ms
​ :active, 16 00:01:55.188, 769ms
​ :16 00:01:55.957, 277ms
​ :active, 16 00:01:56.234, 769ms
​ :16 00:01:57.003, 605ms
​ :crit, 16 00:01:57.608, 768ms
​ :16 00:01:58.376, 3659ms
​ :crit, 16 00:02:02.035, 768ms
​161 tests :16 00:02:02.803, 22557ms
​ :crit, 16 00:02:25.360, 769ms
​154 tests :16 00:02:26.129, 19191ms
​ :crit, 16 00:02:45.320, 769ms
​ :16 00:02:46.089, 12382ms
​ :active, 16 00:02:58.471, 768ms
​ :16 00:02:59.239, 2202ms
section ​ForkJoinPool-1-worker-14
​ :16 00:00:00.000, 2382ms
​ :crit, 16 00:00:02.382, 768ms
​ :16 00:00:03.150, 5172ms
​ :crit, 16 00:00:08.322, 768ms
​ :16 00:00:09.090, 14575ms
​ :active, 16 00:00:23.665, 768ms
​ :16 00:00:24.433, 189ms
​ :active, 16 00:00:24.622, 768ms
​ :16 00:00:25.390, 10528ms
​ :crit, 16 00:00:35.918, 768ms
​ :16 00:00:36.686, 10583ms
​ :crit, 16 00:00:47.270, 769ms
​ :16 00:00:48.039, 12051ms
​ :crit, 16 00:01:00.090, 769ms
​ :16 00:01:00.859, 2748ms
​ :active, 16 00:01:03.607, 768ms
​ :16 00:01:04.375, 1803ms
​ :active, 16 00:01:06.178, 768ms
​ :16 00:01:06.946, 6901ms
​ :crit, 16 00:01:13.847, 768ms
​ :16 00:01:14.615, 8887ms
​ :active, 16 00:01:23.502, 768ms
​ :16 00:01:24.270, 5320ms
​ :active, 16 00:01:29.590, 768ms
​156 tests :16 00:01:30.358, 24428ms
​ :active, 16 00:01:54.786, 769ms
​ :16 00:01:55.555, 2167ms
​ :crit, 16 00:01:57.722, 769ms
​ :16 00:01:58.491, 5825ms
​ :crit, 16 00:02:04.316, 768ms
​ :16 00:02:05.084, 8934ms
​ :crit, 16 00:02:14.018, 768ms
​ :16 00:02:14.786, 7515ms
​ :crit, 16 00:02:22.301, 768ms
​101 tests :16 00:02:23.069, 16950ms
​ :crit, 16 00:02:40.019, 769ms
​ :16 00:02:40.788, 8180ms
​ :active, 16 00:02:48.968, 768ms
​ :16 00:02:49.736, 1563ms
​ :crit, 16 00:02:51.299, 769ms
​ :16 00:02:52.068, 4484ms
​ :crit, 16 00:02:56.552, 768ms
​ :16 00:02:57.320, 3247ms
​ :crit, 16 00:03:00.567, 768ms
​ :16 00:03:01.335, 2388ms
​ :crit, 16 00:03:03.723, 769ms
​ :16 00:03:04.492, 407ms
section ​ForkJoinPool-1-worker-15
​ :16 00:00:00.000, 8837ms
​ :crit, milestone, 16 00:00:11.911, 0ms
​ :16 00:00:14.984, 14123ms
​ :crit, milestone, 16 00:00:32.181, 0ms
​ :16 00:00:35.254, 7449ms
​ :active, milestone, 16 00:00:45.777, 0ms
​441 tests :16 00:00:48.850, 66591ms
​ :crit, milestone, 16 00:01:58.515, 0ms
​ :16 00:02:01.588, 10603ms
​ :active, milestone, 16 00:02:15.265, 0ms
​156 tests :16 00:02:18.338, 24984ms
​ :crit, milestone, 16 00:02:46.396, 0ms
​ :16 00:02:49.469, 4796ms
​ :active, milestone, 16 00:02:57.339, 0ms
​ :16 00:03:00.413, 4356ms
section ​ForkJoinPool-1-worker-16
​ :16 00:00:00.000, 5035ms
​ :crit, 16 00:00:05.035, 769ms
​ :16 00:00:05.804, 10044ms
​ :crit, 16 00:00:15.848, 769ms
​ :16 00:00:16.617, 9771ms
​ :crit, 16 00:00:26.388, 768ms
​ :16 00:00:27.156, 1160ms
​ :active, 16 00:00:28.316, 768ms
​ :16 00:00:29.084, 3102ms
​ :crit, 16 00:00:32.186, 768ms
​ :16 00:00:32.954, 266ms
​ :active, 16 00:00:33.220, 769ms
​ :16 00:00:33.989, 5711ms
​ :active, 16 00:00:39.700, 769ms
​94 tests :16 00:00:40.469, 14793ms
​ :crit, 16 00:00:55.262, 769ms
​ :crit, 16 00:00:56.031, 665ms
​ :16 00:00:56.696, 5865ms
​ :crit, 16 00:01:02.561, 769ms
​231 tests :16 00:01:03.330, 36532ms
​ :active, 16 00:01:39.862, 768ms
​ :16 00:01:40.630, 9873ms
​ :active, 16 00:01:50.503, 768ms
​ :16 00:01:51.271, 12607ms
​ :active, 16 00:02:03.878, 768ms
​ :16 00:02:04.646, 3639ms
​ :crit, 16 00:02:08.285, 769ms
​124 tests :16 00:02:09.054, 17685ms
​ :crit, 16 00:02:26.739, 768ms
​ :16 00:02:27.507, 5472ms
​ :active, 16 00:02:32.979, 769ms
​212 tests :16 00:02:33.748, 32418ms
​ :crit, 16 00:03:06.166, 769ms
​ :16 00:03:06.935, 5857ms
```

<sub>🟩 passed · 🟥 failed · 🟨 failed, then retried (dashed) · ♦️ failed too fast for a bar · 0:00 = 2026-10-21 08:16:56.000 UTC</sub>
<br><sub>Tests too short to tell apart are merged into one bar.</sub>

| Thread | Tests | Earlier attempts | Failed | Busy | Idle | Utilization |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: |
| ForkJoinPool-1-worker-1 | 1250 | 4 | 13 | 3m 6s | 9.3s | 95% |
| ForkJoinPool-1-worker-2 | 1250 | 5 | 9 | 3m 6s | 10.2s | 95% |
| ForkJoinPool-1-worker-3 | 1250 | 13 | 11 | 3m 2s | 13.9s | 93% |
| ForkJoinPool-1-worker-4 | 1250 | 5 | 13 | 3m 3s | 13.1s | 93% |
| ForkJoinPool-1-worker-5 | 1250 | 2 | 11 | 3m 11s | 5.0s | 97% |
| ForkJoinPool-1-worker-6 | 1250 | 9 | 12 | 3m 11s | 4.6s | 98% |
| ForkJoinPool-1-worker-7 | 1250 | 8 | 10 | 3m 2s | 13.6s | 93% |
| ForkJoinPool-1-worker-8 | 1250 | 7 | 12 | 3m 13s | 3.1s | 98% |
| ForkJoinPool-1-worker-9 | 1250 | 7 | 11 | 3m 10s | 5.4s | 97% |
| ForkJoinPool-1-worker-10 | 1250 | 3 | 11 | 3m 7s | 8.4s | 96% |
| ForkJoinPool-1-worker-11 | 1250 | 4 | 11 | 3m 9s | 6.5s | 97% |
| ForkJoinPool-1-worker-12 | 1250 | 11 | 13 | 3m 12s | 3.8s | 98% |
| ForkJoinPool-1-worker-13 | 1250 | 6 | 18 | 2m 58s | 17.8s | 91% |
| ForkJoinPool-1-worker-14 | 1250 | 8 | 15 | 3m 1s | 14.6s | 93% |
| ForkJoinPool-1-worker-15 | 1250 | 4 | 5 | 3m 1s | 14.6s | 93% |
| ForkJoinPool-1-worker-16 | 1250 | 7 | 11 | 3m 9s | 6.7s | 97% |

<details>
<summary>Test timings (100 of 20103: failures)</summary>

| Start | Duration | Thread | Test | Status |
| ---: | ---: | :--- | :--- | :--- |
| 0:58.847 | 167ms | ForkJoinPool-1-worker-1 | suite 0 test 418 | ❌ failed |
| 1:15.810 | 131ms | ForkJoinPool-1-worker-1 | suite 0 test 538 | ❌ failed |
| 1:26.684 | 248ms | ForkJoinPool-1-worker-1 | suite 0 test 596 | ❌ failed |
| 1:33.517 | 151ms | ForkJoinPool-1-worker-1 | suite 0 test 642 | ❌ failed |
| 1:42.264 | 120ms | ForkJoinPool-1-worker-1 | suite 0 test 687 | ❌ failed |
| 2:04.712 | 196ms | ForkJoinPool-1-worker-1 | suite 0 test 843 | ❌ failed |
| 2:17.659 | 147ms | ForkJoinPool-1-worker-1 | suite 0 test 925 | ❌ failed |
| 2:28.265 | 113ms | ForkJoinPool-1-worker-1 | suite 0 test 996 | ❌ failed |
| 2:43.741 | 256ms | ForkJoinPool-1-worker-1 | suite 0 test 1095 | ❌ failed |
| 3:01.017 | 115ms | ForkJoinPool-1-worker-1 | suite 0 test 1203 | ❌ failed |
| 0:05.775 | 147ms | ForkJoinPool-1-worker-2 | suite 1 test 41 | ❌ failed |
| 1:10.301 | 168ms | ForkJoinPool-1-worker-2 | suite 1 test 506 | ❌ failed |
| 1:19.148 | 335ms | ForkJoinPool-1-worker-2 | suite 1 test 561 | ❌ failed |
| 2:05.801 | 140ms | ForkJoinPool-1-worker-2 | suite 1 test 834 | ❌ failed |
| 0:24.559 | 156ms | ForkJoinPool-1-worker-3 | suite 2 test 164 | ❌ failed |
| 0:51.884 | 374ms | ForkJoinPool-1-worker-3 | suite 2 test 345 | ❌ failed |
| 1:21.066 | 420ms | ForkJoinPool-1-worker-3 | suite 2 test 533 | ❌ failed |
| 2:28.395 | 160ms | ForkJoinPool-1-worker-3 | suite 2 test 990 | ❌ failed |
| 0:15.948 | 113ms | ForkJoinPool-1-worker-4 | suite 3 test 105 | ❌ failed |
| 0:30.661 | 614ms | ForkJoinPool-1-worker-4 | suite 3 test 207 | ❌ failed |
| 0:43.746 | 260ms | ForkJoinPool-1-worker-4 | suite 3 test 290 | ❌ failed |
| 1:21.507 | 395ms | ForkJoinPool-1-worker-4 | suite 3 test 543 | ❌ failed |
| 1:56.149 | 248ms | ForkJoinPool-1-worker-4 | suite 3 test 774 | ❌ failed |
| 2:55.947 | 110ms | ForkJoinPool-1-worker-4 | suite 3 test 1176 | ❌ failed |
| 0:06.432 | 563ms | ForkJoinPool-1-worker-5 | suite 4 test 35 | ❌ failed |
| 0:13.648 | 110ms | ForkJoinPool-1-worker-5 | suite 4 test 82 | ❌ failed |
| 0:23.778 | 241ms | ForkJoinPool-1-worker-5 | suite 4 test 151 | ❌ failed |
| 1:17.249 | 416ms | ForkJoinPool-1-worker-5 | suite 4 test 477 | ❌ failed |
| 1:33.295 | 101ms | ForkJoinPool-1-worker-5 | suite 4 test 569 | ❌ failed |
| 1:48.469 | 276ms | ForkJoinPool-1-worker-5 | suite 4 test 658 | ❌ failed |
| 2:51.972 | 263ms | ForkJoinPool-1-worker-5 | suite 4 test 1109 | ❌ failed |
| 0:16.053 | 396ms | ForkJoinPool-1-worker-6 | suite 5 test 89 | ❌ failed |
| 0:21.317 | 117ms | ForkJoinPool-1-worker-6 | suite 5 test 123 | ❌ failed |
| 0:24.243 | 132ms | ForkJoinPool-1-worker-6 | suite 5 test 140 | ❌ failed |
| 0:58.117 | 407ms | ForkJoinPool-1-worker-6 | suite 5 test 372 | ❌ failed |
| 1:20.365 | 193ms | ForkJoinPool-1-worker-6 | suite 5 test 501 | ❌ failed |
| 2:18.001 | 182ms | ForkJoinPool-1-worker-6 | suite 5 test 898 | ❌ failed |
| 2:21.789 | 107ms | ForkJoinPool-1-worker-6 | suite 5 test 926 | ❌ failed |
| 2:27.207 | 120ms | ForkJoinPool-1-worker-6 | suite 5 test 963 | ❌ failed |
| 2:59.487 | 415ms | ForkJoinPool-1-worker-6 | suite 5 test 1164 | ❌ failed |
| 2:59.968 | 132ms | ForkJoinPool-1-worker-6 | suite 5 test 1166 | ❌ failed |
| 0:06.843 | 196ms | ForkJoinPool-1-worker-7 | suite 6 test 44 | ❌ failed |
| 0:39.449 | 245ms | ForkJoinPool-1-worker-7 | suite 6 test 251 | ❌ failed |
| 0:58.219 | 121ms | ForkJoinPool-1-worker-7 | suite 6 test 385 | ❌ failed |
| 1:17.917 | 139ms | ForkJoinPool-1-worker-7 | suite 6 test 508 | ❌ failed |
| 1:55.264 | 150ms | ForkJoinPool-1-worker-7 | suite 6 test 771 | ❌ failed |
| 2:10.043 | 148ms | ForkJoinPool-1-worker-7 | suite 6 test 875 | ❌ failed |
| 2:25.515 | 127ms | ForkJoinPool-1-worker-7 | suite 6 test 971 | ❌ failed |
| 1:06.015 | 145ms | ForkJoinPool-1-worker-8 | suite 7 test 417 | ❌ failed |
| 2:01.430 | 466ms | ForkJoinPool-1-worker-8 | suite 7 test 800 | ❌ failed |
| 2:49.594 | 95ms | ForkJoinPool-1-worker-8 | suite 7 test 1072 | ❌ failed |
| 2:52.097 | 406ms | ForkJoinPool-1-worker-8 | suite 7 test 1092 | ❌ failed |
| 2:58.628 | 134ms | ForkJoinPool-1-worker-8 | suite 7 test 1130 | ❌ failed |
| 0:15.019 | 548ms | ForkJoinPool-1-worker-9 | suite 8 test 99 | ❌ failed |
| 1:04.708 | 218ms | ForkJoinPool-1-worker-9 | suite 8 test 408 | ❌ failed |
| 2:55.346 | 116ms | ForkJoinPool-1-worker-9 | suite 8 test 1131 | ❌ failed |
| 3:02.029 | 121ms | ForkJoinPool-1-worker-9 | suite 8 test 1169 | ❌ failed |
| 0:32.116 | 150ms | ForkJoinPool-1-worker-10 | suite 9 test 196 | ❌ failed |
| 0:50.738 | 132ms | ForkJoinPool-1-worker-10 | suite 9 test 320 | ❌ failed |
| 1:22.211 | 295ms | ForkJoinPool-1-worker-10 | suite 9 test 529 | ❌ failed |
| 2:01.966 | 138ms | ForkJoinPool-1-worker-10 | suite 9 test 783 | ❌ failed |
| 2:11.634 | 200ms | ForkJoinPool-1-worker-10 | suite 9 test 861 | ❌ failed |
| 2:55.193 | 101ms | ForkJoinPool-1-worker-10 | suite 9 test 1147 | ❌ failed |
| 3:01.561 | 262ms | ForkJoinPool-1-worker-10 | suite 9 test 1189 | ❌ failed |
| 0:09.438 | 102ms | ForkJoinPool-1-worker-11 | suite 10 test 58 | ❌ failed |
| 0:15.908 | 238ms | ForkJoinPool-1-worker-11 | suite 10 test 105 | ❌ failed |
| 0:18.771 | 182ms | ForkJoinPool-1-worker-11 | suite 10 test 120 | ❌ failed |
| 1:13.066 | 199ms | ForkJoinPool-1-worker-11 | suite 10 test 462 | ❌ failed |
| 1:56.510 | 157ms | ForkJoinPool-1-worker-11 | suite 10 test 749 | ❌ failed |
| 1:58.381 | 234ms | ForkJoinPool-1-worker-11 | suite 10 test 760 | ❌ failed |
| 2:13.758 | 432ms | ForkJoinPool-1-worker-11 | suite 10 test 861 | ❌ failed |
| 2:18.467 | 101ms | ForkJoinPool-1-worker-11 | suite 10 test 888 | ❌ failed |
| 3:08.803 | 192ms | ForkJoinPool-1-worker-11 | suite 10 test 1223 | ❌ failed |
| 0:03.891 | 180ms | ForkJoinPool-1-worker-12 | suite 11 test 32 | ❌ failed |
| 0:04.543 | 196ms | ForkJoinPool-1-worker-12 | suite 11 test 40 | ❌ failed |
| 0:57.799 | 201ms | ForkJoinPool-1-worker-12 | suite 11 test 386 | ❌ failed |
| 2:11.628 | 98ms | ForkJoinPool-1-worker-12 | suite 11 test 867 | ❌ failed |
| 2:48.790 | 407ms | ForkJoinPool-1-worker-12 | suite 11 test 1082 | ❌ failed |
| 2:59.143 | 237ms | ForkJoinPool-1-worker-12 | suite 11 test 1150 | ❌ failed |
| 3:06.890 | 137ms | ForkJoinPool-1-worker-12 | suite 11 test 1192 | ❌ failed |
| 0:17.649 | 200ms | ForkJoinPool-1-worker-13 | suite 12 test 114 | ❌ failed |
| 1:03.914 | 136ms | ForkJoinPool-1-worker-13 | suite 12 test 434 | ❌ failed |
| 1:08.477 | 201ms | ForkJoinPool-1-worker-13 | suite 12 test 461 | ❌ failed |
| 1:26.650 | 128ms | ForkJoinPool-1-worker-13 | suite 12 test 567 | ❌ failed |
| 1:39.871 | 181ms | ForkJoinPool-1-worker-13 | suite 12 test 657 | ❌ failed |
| 1:51.353 | 386ms | ForkJoinPool-1-worker-13 | suite 12 test 729 | ❌ failed |
| 2:02.144 | 501ms | ForkJoinPool-1-worker-13 | suite 12 test 801 | ❌ failed |
| 2:25.645 | 199ms | ForkJoinPool-1-worker-13 | suite 12 test 967 | ❌ failed |
| 0:36.229 | 146ms | ForkJoinPool-1-worker-14 | suite 13 test 246 | ❌ failed |
| 0:47.415 | 479ms | ForkJoinPool-1-worker-14 | suite 13 test 326 | ❌ failed |
| 1:00.384 | 181ms | ForkJoinPool-1-worker-14 | suite 13 test 417 | ❌ failed |
| 2:14.292 | 220ms | ForkJoinPool-1-worker-14 | suite 13 test 909 | ❌ failed |
| 2:22.630 | 110ms | ForkJoinPool-1-worker-14 | suite 13 test 968 | ❌ failed |
| 2:40.154 | 499ms | ForkJoinPool-1-worker-14 | suite 13 test 1071 | ❌ failed |
| 2:56.788 | 296ms | ForkJoinPool-1-worker-14 | suite 13 test 1190 | ❌ failed |
| 0:11.845 | 131ms | ForkJoinPool-1-worker-15 | suite 14 test 83 | ❌ failed |
| 1:58.465 | 99ms | ForkJoinPool-1-worker-15 | suite 14 test 806 | ❌ failed |
| 2:48.822 | 152ms | ForkJoinPool-1-worker-15 | suite 14 test 1134 | ❌ failed |
| 0:32.498 | 144ms | ForkJoinPool-1-worker-16 | suite 15 test 236 | ❌ failed |
| 0:55.557 | 179ms | ForkJoinPool-1-worker-16 | suite 15 test 375 | ❌ failed |

</details>

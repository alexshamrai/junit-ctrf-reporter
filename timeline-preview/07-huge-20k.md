### Timeline

**20000** tests and **103** earlier attempts on **16** threads in **3m 16s** · busy **95%** of the time · up to **16** at once

```mermaid
---
config:
  gantt:
    displayMode: compact
    useWidth: 1000
    leftPadding: 192
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
accDescr: 20000 tests on 16 threads over 3m 16s, 186 failed
dateFormat D HH:mm:ss.SSS
axisFormat %-M:%S
todayMarker off
section ​ForkJoinPool-1-worker-1
​198 tests :16 00:00:00.000, 28100ms
​ :crit, 16 00:00:28.100, 767ms
​211 tests :16 00:00:28.867, 29680ms
​ :crit, 16 00:00:58.547, 767ms
​ :16 00:00:59.314, 7533ms
​ :crit, 16 00:01:06.847, 766ms
​ :16 00:01:07.613, 2845ms
​ :active, 16 00:01:10.458, 766ms
​ :16 00:01:11.224, 4268ms
​ :crit, 16 00:01:15.492, 767ms
​ :16 00:01:16.259, 6089ms
​ :active, 16 00:01:22.348, 766ms
​ :16 00:01:23.114, 3311ms
​ :crit, 16 00:01:26.425, 766ms
​ :16 00:01:27.191, 6018ms
​ :crit, 16 00:01:33.209, 767ms
​ :16 00:01:33.976, 7965ms
​ :crit, 16 00:01:41.941, 766ms
​149 tests :16 00:01:42.707, 21720ms
​ :crit, 16 00:02:04.427, 766ms
​ :16 00:02:05.193, 7408ms
​ :active, 16 00:02:12.601, 767ms
​ :16 00:02:13.368, 3981ms
​ :crit, 16 00:02:17.349, 767ms
​ :16 00:02:18.116, 5432ms
​ :crit, 16 00:02:23.548, 766ms
​ :16 00:02:24.314, 3624ms
​ :crit, 16 00:02:27.938, 767ms
​ :16 00:02:28.705, 9551ms
​ :active, 16 00:02:38.256, 766ms
​ :16 00:02:39.022, 4464ms
​ :crit, 16 00:02:43.486, 766ms
​103 tests :16 00:02:44.252, 16439ms
​ :crit, 16 00:03:00.691, 767ms
​ :16 00:03:01.458, 8553ms
section ​ForkJoinPool-1-worker-2
​ :16 00:00:00.000, 4209ms
​ :active, 16 00:00:04.209, 766ms
​ :16 00:00:04.975, 490ms
​ :crit, 16 00:00:05.465, 767ms
​455 tests :16 00:00:06.232, 63770ms
​ :crit, 16 00:01:10.002, 766ms
​ :16 00:01:10.768, 1346ms
​ :crit, 16 00:01:12.114, 767ms
​ :active, 16 00:01:12.881, 603ms
​ :16 00:01:13.484, 3058ms
​ :crit, 16 00:01:16.542, 767ms
​ :16 00:01:17.309, 1623ms
​ :crit, 16 00:01:18.932, 767ms
​93 tests :16 00:01:19.699, 14911ms
​ :crit, 16 00:01:34.610, 766ms
​ :16 00:01:35.376, 13232ms
​ :active, 16 00:01:48.608, 766ms
​88 tests :16 00:01:49.374, 16114ms
​ :crit, 16 00:02:05.488, 766ms
​ :16 00:02:06.254, 209ms
​ :crit, 16 00:02:06.463, 766ms
​ :16 00:02:07.229, 5159ms
​ :crit, 16 00:02:12.388, 767ms
​ :active, 16 00:02:13.155, 721ms
​143 tests :16 00:02:13.876, 25469ms
​ :active, 16 00:02:39.345, 766ms
​202 tests :16 00:02:40.111, 29052ms
section ​ForkJoinPool-1-worker-3
​110 tests :16 00:00:00.000, 15996ms
​ :active, 16 00:00:15.996, 767ms
​ :16 00:00:16.763, 7489ms
​ :crit, 16 00:00:24.252, 767ms
​ :16 00:00:25.019, 1504ms
​ :crit, 16 00:00:26.523, 766ms
​ :16 00:00:27.289, 11601ms
​ :active, 16 00:00:38.890, 766ms
​ :16 00:00:39.656, 7064ms
​ :crit, 16 00:00:46.720, 767ms
​ :16 00:00:47.487, 2833ms
​ :crit, 16 00:00:50.320, 766ms
​ :16 00:00:51.086, 602ms
​ :crit, 16 00:00:51.688, 766ms
​ :16 00:00:52.454, 10497ms
​ :active, 16 00:01:02.951, 766ms
​ :16 00:01:03.717, 5744ms
​ :active, 16 00:01:09.461, 766ms
​ :16 00:01:10.227, 6740ms
​ :active, 16 00:01:16.967, 767ms
​ :16 00:01:17.734, 3159ms
​ :crit, 16 00:01:20.893, 766ms
​ :16 00:01:21.659, 5006ms
​ :active, 16 00:01:26.665, 766ms
​ :16 00:01:27.431, 3621ms
​ :active, 16 00:01:31.052, 766ms
​248 tests :16 00:01:31.818, 36095ms
​ :active, 16 00:02:07.913, 767ms
​ :16 00:02:08.680, 109ms
​ :active, 16 00:02:08.789, 766ms
​ :16 00:02:09.555, 4051ms
​ :active, 16 00:02:13.606, 767ms
​ :16 00:02:14.373, 1819ms
​ :crit, 16 00:02:16.192, 766ms
​ :16 00:02:16.958, 9831ms
​ :active, 16 00:02:26.789, 766ms
​ :16 00:02:27.555, 537ms
​ :crit, 16 00:02:28.092, 766ms
​ :16 00:02:28.858, 4289ms
​ :crit, 16 00:02:33.147, 767ms
​ :16 00:02:33.914, 10660ms
​ :active, 16 00:02:44.574, 767ms
​ :16 00:02:45.341, 8263ms
​ :active, 16 00:02:53.604, 767ms
​ :16 00:02:54.371, 11106ms
section ​ForkJoinPool-1-worker-4
​ :16 00:00:00.000, 7167ms
​ :crit, 16 00:00:07.167, 766ms
​ :16 00:00:07.933, 2087ms
​ :crit, 16 00:00:10.020, 766ms
​ :16 00:00:10.786, 1245ms
​ :crit, 16 00:00:12.031, 767ms
​ :16 00:00:12.798, 2823ms
​ :crit, 16 00:00:15.621, 767ms
​ :16 00:00:16.388, 6616ms
​ :active, 16 00:00:23.004, 767ms
​ :16 00:00:23.771, 6814ms
​ :crit, 16 00:00:30.585, 766ms
​ :16 00:00:31.351, 7865ms
​ :active, 16 00:00:39.216, 766ms
​ :16 00:00:39.982, 516ms
​ :crit, 16 00:00:40.498, 767ms
​ :16 00:00:41.265, 2228ms
​ :crit, 16 00:00:43.493, 766ms
​98 tests :16 00:00:44.259, 15097ms
​ :active, 16 00:00:59.356, 767ms
​ :16 00:01:00.123, 2157ms
​ :crit, 16 00:01:02.280, 767ms
​ :16 00:01:03.047, 5032ms
​ :active, 16 00:01:08.079, 767ms
​ :16 00:01:08.846, 12475ms
​ :crit, 16 00:01:21.321, 767ms
​129 tests :16 00:01:22.088, 18657ms
​ :active, 16 00:01:40.745, 766ms
​99 tests :16 00:01:41.511, 14379ms
​ :crit, 16 00:01:55.890, 766ms
​157 tests :16 00:01:56.656, 22267ms
​ :crit, 16 00:02:18.923, 767ms
​230 tests :16 00:02:19.690, 35929ms
​ :crit, 16 00:02:55.619, 766ms
​ :16 00:02:56.385, 9872ms
section ​ForkJoinPool-1-worker-5
​ :16 00:00:00.000, 6330ms
​ :crit, 16 00:00:06.330, 767ms
​ :16 00:00:07.097, 1945ms
​ :crit, 16 00:00:09.042, 767ms
​ :16 00:00:09.809, 3511ms
​ :crit, 16 00:00:13.320, 766ms
​ :16 00:00:14.086, 9429ms
​ :crit, 16 00:00:23.515, 767ms
​99 tests :16 00:00:24.282, 17287ms
​ :active, 16 00:00:41.569, 766ms
​ :16 00:00:42.335, 11026ms
​ :crit, 16 00:00:53.361, 767ms
​151 tests :16 00:00:54.128, 22946ms
​ :crit, 16 00:01:17.074, 766ms
​90 tests :16 00:01:17.840, 15122ms
​ :crit, 16 00:01:32.962, 767ms
​ :16 00:01:33.729, 7698ms
​ :active, 16 00:01:41.427, 767ms
​ :16 00:01:42.194, 6030ms
​ :crit, 16 00:01:48.224, 766ms
​440 tests :16 00:01:48.990, 61713ms
​ :crit, 16 00:02:50.703, 766ms
​ :16 00:02:51.469, 251ms
​ :crit, 16 00:02:51.720, 767ms
​ :16 00:02:52.487, 13261ms
​ :crit, 16 00:03:05.748, 766ms
​ :16 00:03:06.514, 7841ms
section ​ForkJoinPool-1-worker-6
​ :16 00:00:00.000, 9002ms
​ :active, 16 00:00:09.002, 767ms
​ :16 00:00:09.769, 6099ms
​ :crit, 16 00:00:15.868, 766ms
​ :16 00:00:16.634, 4358ms
​ :crit, 16 00:00:20.992, 767ms
​ :16 00:00:21.759, 1819ms
​ :active, 16 00:00:23.578, 766ms
​ :crit, 16 00:00:24.344, 348ms
​140 tests :16 00:00:24.692, 21532ms
​ :active, 16 00:00:46.224, 766ms
​ :16 00:00:46.990, 639ms
​ :active, 16 00:00:47.629, 766ms
​ :16 00:00:48.395, 9542ms
​ :crit, 16 00:00:57.937, 767ms
​108 tests :16 00:00:58.704, 17264ms
​ :active, 16 00:01:15.968, 766ms
​ :16 00:01:16.734, 3344ms
​ :crit, 16 00:01:20.078, 767ms
​238 tests :16 00:01:20.845, 33423ms
​ :active, 16 00:01:54.268, 767ms
​149 tests :16 00:01:55.035, 22674ms
​ :crit, 16 00:02:17.709, 766ms
​ :16 00:02:18.475, 2984ms
​ :crit, 16 00:02:21.459, 767ms
​ :16 00:02:22.226, 4658ms
​ :crit, 16 00:02:26.884, 766ms
​ :active, 16 00:02:27.650, 507ms
​ :16 00:02:28.157, 3137ms
​ :crit, 16 00:02:31.294, 767ms
​134 tests :16 00:02:32.061, 20683ms
​ :active, 16 00:02:52.744, 767ms
​ :16 00:02:53.511, 3049ms
​ :active, 16 00:02:56.560, 766ms
​ :16 00:02:57.326, 2084ms
​ :crit, 16 00:02:59.410, 767ms
​ :16 00:03:00.177, 10674ms
​ :crit, 16 00:03:10.851, 766ms
​ :16 00:03:11.617, 3159ms
section ​ForkJoinPool-1-worker-7
​ :16 00:00:00.000, 6042ms
​ :crit, 16 00:00:06.042, 766ms
​ :crit, 16 00:00:06.808, 516ms
​95 tests :16 00:00:07.324, 15972ms
​ :active, 16 00:00:23.296, 766ms
​ :16 00:00:24.062, 1408ms
​ :active, 16 00:00:25.470, 766ms
​ :16 00:00:26.236, 2412ms
​ :active, 16 00:00:28.648, 767ms
​ :16 00:00:29.415, 9773ms
​ :crit, 16 00:00:39.188, 767ms
​ :active, 16 00:00:39.955, 442ms
​ :16 00:00:40.397, 8844ms
​ :active, 16 00:00:49.241, 767ms
​ :16 00:00:50.008, 952ms
​ :active, 16 00:00:50.960, 767ms
​ :16 00:00:51.727, 6169ms
​ :crit, 16 00:00:57.896, 767ms
​92 tests :16 00:00:58.663, 15868ms
​ :crit, 16 00:01:14.531, 766ms
​ :16 00:01:15.297, 2306ms
​ :crit, 16 00:01:17.603, 767ms
​210 tests :16 00:01:18.370, 31104ms
​ :crit, 16 00:01:49.474, 767ms
​ :16 00:01:50.241, 4715ms
​ :crit, 16 00:01:54.956, 766ms
​96 tests :16 00:01:55.722, 14012ms
​ :crit, 16 00:02:09.734, 766ms
​88 tests :16 00:02:10.500, 14631ms
​ :crit, 16 00:02:25.131, 766ms
​ :16 00:02:25.897, 10082ms
​ :active, 16 00:02:35.979, 767ms
​193 tests :16 00:02:36.746, 28971ms
section ​ForkJoinPool-1-worker-8
​307 tests :16 00:00:00.000, 49540ms
​ :active, 16 00:00:49.540, 766ms
​ :16 00:00:50.306, 10636ms
​ :crit, 16 00:01:00.942, 767ms
​ :16 00:01:01.709, 3995ms
​ :crit, 16 00:01:05.704, 767ms
​ :active, 16 00:01:06.471, 400ms
​ :16 00:01:06.871, 2128ms
​ :crit, 16 00:01:08.999, 766ms
​110 tests :16 00:01:09.765, 16924ms
​ :active, 16 00:01:26.689, 766ms
​ :16 00:01:27.455, 14717ms
​ :active, 16 00:01:42.172, 767ms
​ :16 00:01:42.939, 463ms
​ :active, 16 00:01:43.402, 766ms
​ :16 00:01:44.168, 8081ms
​ :crit, 16 00:01:52.249, 767ms
​ :16 00:01:53.016, 80ms
​ :active, 16 00:01:53.096, 766ms
​ :16 00:01:53.862, 7418ms
​ :crit, 16 00:02:01.280, 766ms
​ :16 00:02:02.046, 6108ms
​ :crit, 16 00:02:08.154, 767ms
​108 tests :16 00:02:08.921, 18158ms
​ :crit, 16 00:02:27.079, 767ms
​ :16 00:02:27.846, 10638ms
​ :crit, 16 00:02:38.484, 766ms
​ :16 00:02:39.250, 10008ms
​ :crit, 16 00:02:49.258, 767ms
​ :16 00:02:50.025, 1892ms
​ :crit, 16 00:02:51.917, 766ms
​ :16 00:02:52.683, 5629ms
​ :crit, 16 00:02:58.312, 766ms
​117 tests :16 00:02:59.078, 17115ms
section ​ForkJoinPool-1-worker-9
​99 tests :16 00:00:00.000, 14910ms
​ :crit, 16 00:00:14.910, 766ms
​ :16 00:00:15.676, 3820ms
​ :crit, 16 00:00:19.496, 766ms
​ :16 00:00:20.262, 7209ms
​ :active, 16 00:00:27.471, 767ms
​193 tests :16 00:00:28.238, 28806ms
​ :active, 16 00:00:57.044, 767ms
​ :16 00:00:57.811, 6623ms
​ :crit, 16 00:01:04.434, 766ms
​113 tests :16 00:01:05.200, 19049ms
​ :active, 16 00:01:24.249, 767ms
​ :16 00:01:25.016, 939ms
​ :active, 16 00:01:25.955, 766ms
​ :16 00:01:26.721, 2070ms
​ :active, 16 00:01:28.791, 767ms
​ :16 00:01:29.558, 13768ms
​ :crit, 16 00:01:43.326, 766ms
​ :16 00:01:44.092, 13054ms
​ :crit, 16 00:01:57.146, 767ms
​ :16 00:01:57.913, 3964ms
​ :active, 16 00:02:01.877, 767ms
​ :16 00:02:02.644, 11237ms
​ :crit, 16 00:02:13.881, 766ms
​ :16 00:02:14.647, 6639ms
​ :crit, 16 00:02:21.286, 766ms
​190 tests :16 00:02:22.052, 28419ms
​ :crit, 16 00:02:50.471, 766ms
​ :16 00:02:51.237, 2798ms
​ :active, 16 00:02:54.035, 766ms
​ :16 00:02:54.801, 220ms
​ :crit, 16 00:02:55.021, 766ms
​ :16 00:02:55.787, 5919ms
​ :crit, 16 00:03:01.706, 767ms
​ :crit, 16 00:03:02.473, 602ms
​ :16 00:03:03.075, 10852ms
section ​ForkJoinPool-1-worker-10
​191 tests :16 00:00:00.000, 31808ms
​ :crit, 16 00:00:31.808, 766ms
​ :16 00:00:32.574, 3850ms
​ :crit, 16 00:00:36.424, 767ms
​ :16 00:00:37.191, 13230ms
​ :crit, 16 00:00:50.421, 766ms
​122 tests :16 00:00:51.187, 18991ms
​ :active, 16 00:01:10.178, 767ms
​ :16 00:01:10.945, 4228ms
​ :active, 16 00:01:15.173, 766ms
​ :16 00:01:15.939, 6036ms
​ :crit, 16 00:01:21.975, 767ms
​112 tests :16 00:01:22.742, 17144ms
​ :active, 16 00:01:39.886, 766ms
​ :16 00:01:40.652, 7879ms
​ :crit, 16 00:01:48.531, 767ms
​ :16 00:01:49.298, 12354ms
​ :crit, 16 00:02:01.652, 766ms
​ :16 00:02:02.418, 424ms
​ :crit, 16 00:02:02.842, 767ms
​ :16 00:02:03.609, 2002ms
​ :crit, 16 00:02:05.611, 766ms
​ :16 00:02:06.377, 4974ms
​ :crit, 16 00:02:11.351, 766ms
​280 tests :16 00:02:12.117, 42743ms
​ :crit, 16 00:02:54.860, 767ms
​ :16 00:02:55.627, 5682ms
​ :crit, 16 00:03:01.309, 766ms
​ :16 00:03:02.075, 8919ms
section ​ForkJoinPool-1-worker-11
​ :16 00:00:00.000, 9153ms
​ :crit, 16 00:00:09.153, 767ms
​ :16 00:00:09.920, 5724ms
​ :crit, 16 00:00:15.644, 766ms
​ :16 00:00:16.410, 2069ms
​ :crit, 16 00:00:18.479, 766ms
​278 tests :16 00:00:19.245, 42318ms
​ :active, 16 00:01:01.563, 766ms
​ :16 00:01:02.329, 10453ms
​ :crit, 16 00:01:12.782, 767ms
​116 tests :16 00:01:13.549, 18768ms
​ :active, 16 00:01:32.317, 766ms
​160 tests :16 00:01:33.083, 23122ms
​ :crit, 16 00:01:56.205, 767ms
​ :16 00:01:56.972, 1143ms
​ :crit, 16 00:01:58.115, 766ms
​96 tests :16 00:01:58.881, 14710ms
​ :crit, 16 00:02:13.591, 766ms
​ :16 00:02:14.357, 3777ms
​ :crit, 16 00:02:18.134, 767ms
​ :16 00:02:18.901, 1764ms
​ :crit, 16 00:02:20.665, 767ms
​212 tests :16 00:02:21.432, 32128ms
​ :active, 16 00:02:53.560, 767ms
​ :16 00:02:54.327, 8799ms
​ :active, 16 00:03:03.126, 766ms
​ :16 00:03:03.892, 4624ms
​ :crit, 16 00:03:08.516, 766ms
​ :16 00:03:09.282, 3466ms
section ​ForkJoinPool-1-worker-12
​ :16 00:00:00.000, 3598ms
​ :crit, 16 00:00:03.598, 766ms
​ :crit, 16 00:00:04.364, 660ms
​ :16 00:00:05.024, 6896ms
​ :active, 16 00:00:11.920, 767ms
​ :16 00:00:12.687, 15228ms
​ :active, 16 00:00:27.915, 767ms
​90 tests :16 00:00:28.682, 16549ms
​ :crit, 16 00:00:45.231, 767ms
​ :active, 16 00:00:45.998, 473ms
​ :16 00:00:46.471, 6621ms
​ :crit, 16 00:00:53.092, 767ms
​ :16 00:00:53.859, 3657ms
​ :crit, 16 00:00:57.516, 767ms
​ :16 00:00:58.283, 6224ms
​ :crit, 16 00:01:04.507, 766ms
​ :16 00:01:05.273, 1158ms
​ :crit, 16 00:01:06.431, 767ms
​ :16 00:01:07.198, 11304ms
​ :active, 16 00:01:18.502, 767ms
​120 tests :16 00:01:19.269, 17434ms
​ :active, 16 00:01:36.703, 767ms
​ :16 00:01:37.470, 2648ms
​ :active, 16 00:01:40.118, 766ms
​ :16 00:01:40.884, 1060ms
​ :active, 16 00:01:41.944, 767ms
​ :16 00:01:42.711, 4211ms
​ :crit, 16 00:01:46.922, 767ms
​ :16 00:01:47.689, 2489ms
​ :active, 16 00:01:50.178, 766ms
​ :16 00:01:50.944, 6274ms
​ :crit, 16 00:01:57.218, 766ms
​ :16 00:01:57.984, 5481ms
​ :active, 16 00:02:03.465, 767ms
​ :16 00:02:04.232, 7062ms
​ :crit, 16 00:02:11.294, 766ms
​156 tests :16 00:02:12.060, 28318ms
​ :active, 16 00:02:40.378, 766ms
​ :16 00:02:41.144, 6207ms
​ :active, 16 00:02:47.351, 766ms
​ :16 00:02:48.117, 493ms
​ :crit, 16 00:02:48.610, 767ms
​ :16 00:02:49.377, 9501ms
​ :crit, 16 00:02:58.878, 767ms
​ :16 00:02:59.645, 6930ms
​ :crit, 16 00:03:06.575, 767ms
​ :16 00:03:07.342, 8164ms
section ​ForkJoinPool-1-worker-13
​ :16 00:00:00.000, 13557ms
​ :crit, 16 00:00:13.557, 767ms
​ :16 00:00:14.324, 3042ms
​ :crit, 16 00:00:17.366, 766ms
​225 tests :16 00:00:18.132, 32656ms
​ :active, 16 00:00:50.788, 767ms
​ :16 00:00:51.555, 12044ms
​ :crit, 16 00:01:03.599, 766ms
​ :16 00:01:04.365, 3829ms
​ :crit, 16 00:01:08.194, 767ms
​ :16 00:01:08.961, 12800ms
​ :crit, 16 00:01:21.761, 767ms
​ :16 00:01:22.528, 3803ms
​ :crit, 16 00:01:26.331, 766ms
​ :16 00:01:27.097, 2666ms
​ :crit, 16 00:01:29.763, 767ms
​ :16 00:01:30.530, 1450ms
​ :active, 16 00:01:31.980, 766ms
​ :16 00:01:32.746, 4340ms
​ :crit, 16 00:01:37.086, 767ms
​ :16 00:01:37.853, 1725ms
​ :crit, 16 00:01:39.578, 767ms
​ :16 00:01:40.345, 907ms
​ :crit, 16 00:01:41.252, 767ms
​ :16 00:01:42.019, 4613ms
​ :crit, 16 00:01:46.632, 766ms
​ :16 00:01:47.398, 2192ms
​ :crit, 16 00:01:49.590, 766ms
​ :16 00:01:50.356, 807ms
​ :crit, 16 00:01:51.163, 766ms
​ :16 00:01:51.929, 2265ms
​ :active, 16 00:01:54.194, 767ms
​ :16 00:01:54.961, 228ms
​ :active, 16 00:01:55.189, 767ms
​ :16 00:01:55.956, 279ms
​ :active, 16 00:01:56.235, 767ms
​ :16 00:01:57.002, 607ms
​ :crit, 16 00:01:57.609, 766ms
​ :16 00:01:58.375, 3661ms
​ :crit, 16 00:02:02.036, 766ms
​163 tests :16 00:02:02.802, 22559ms
​ :crit, 16 00:02:25.361, 767ms
​153 tests :16 00:02:26.128, 19193ms
​ :crit, 16 00:02:45.321, 767ms
​ :16 00:02:46.088, 12384ms
​ :active, 16 00:02:58.472, 766ms
​ :16 00:02:59.238, 2203ms
section ​ForkJoinPool-1-worker-14
​ :16 00:00:00.000, 2383ms
​ :crit, 16 00:00:02.383, 766ms
​ :16 00:00:03.149, 5174ms
​ :crit, 16 00:00:08.323, 766ms
​ :16 00:00:09.089, 14577ms
​ :active, 16 00:00:23.666, 766ms
​ :16 00:00:24.432, 191ms
​ :active, 16 00:00:24.623, 766ms
​ :16 00:00:25.389, 10530ms
​ :crit, 16 00:00:35.919, 766ms
​ :16 00:00:36.685, 10586ms
​ :crit, 16 00:00:47.271, 767ms
​ :16 00:00:48.038, 12053ms
​ :crit, 16 00:01:00.091, 767ms
​ :16 00:01:00.858, 2750ms
​ :active, 16 00:01:03.608, 766ms
​ :16 00:01:04.374, 1805ms
​ :active, 16 00:01:06.179, 766ms
​ :16 00:01:06.945, 6903ms
​ :crit, 16 00:01:13.848, 766ms
​ :16 00:01:14.614, 8889ms
​ :active, 16 00:01:23.503, 766ms
​ :16 00:01:24.269, 5322ms
​ :active, 16 00:01:29.591, 766ms
​155 tests :16 00:01:30.357, 24430ms
​ :active, 16 00:01:54.787, 767ms
​ :16 00:01:55.554, 2169ms
​ :crit, 16 00:01:57.723, 767ms
​ :16 00:01:58.490, 5827ms
​ :crit, 16 00:02:04.317, 766ms
​ :16 00:02:05.083, 8936ms
​ :crit, 16 00:02:14.019, 766ms
​ :16 00:02:14.785, 7517ms
​ :crit, 16 00:02:22.302, 766ms
​100 tests :16 00:02:23.068, 16952ms
​ :crit, 16 00:02:40.020, 767ms
​ :16 00:02:40.787, 8182ms
​ :active, 16 00:02:48.969, 766ms
​ :16 00:02:49.735, 1565ms
​ :crit, 16 00:02:51.300, 767ms
​ :16 00:02:52.067, 4486ms
​ :crit, 16 00:02:56.553, 766ms
​ :16 00:02:57.319, 3249ms
​ :crit, 16 00:03:00.568, 766ms
​ :16 00:03:01.334, 2390ms
​ :crit, 16 00:03:03.724, 767ms
​ :16 00:03:04.491, 408ms
section ​ForkJoinPool-1-worker-15
​ :16 00:00:00.000, 11527ms
​ :crit, 16 00:00:11.527, 767ms
​108 tests :16 00:00:12.294, 17092ms
​ :crit, 16 00:00:29.386, 767ms
​ :16 00:00:30.153, 4056ms
​ :crit, 16 00:00:34.209, 766ms
​ :16 00:00:34.975, 10418ms
​ :active, 16 00:00:45.393, 767ms
​480 tests :16 00:00:46.160, 71971ms
​ :crit, 16 00:01:58.131, 767ms
​98 tests :16 00:01:58.898, 15983ms
​ :active, 16 00:02:14.881, 767ms
​176 tests :16 00:02:15.648, 27807ms
​ :active, 16 00:02:43.455, 766ms
​ :16 00:02:44.221, 4294ms
​ :crit, 16 00:02:48.515, 766ms
​ :16 00:02:49.281, 7675ms
​ :active, 16 00:02:56.956, 766ms
​ :16 00:02:57.722, 7047ms
section ​ForkJoinPool-1-worker-16
​ :16 00:00:00.000, 5036ms
​ :crit, 16 00:00:05.036, 767ms
​ :16 00:00:05.803, 10046ms
​ :crit, 16 00:00:15.849, 767ms
​ :16 00:00:16.616, 9773ms
​ :crit, 16 00:00:26.389, 766ms
​ :16 00:00:27.155, 1162ms
​ :active, 16 00:00:28.317, 766ms
​ :16 00:00:29.083, 3104ms
​ :crit, 16 00:00:32.187, 766ms
​ :16 00:00:32.953, 268ms
​ :active, 16 00:00:33.221, 767ms
​ :16 00:00:33.988, 5713ms
​ :active, 16 00:00:39.701, 767ms
​93 tests :16 00:00:40.468, 14795ms
​ :crit, 16 00:00:55.263, 767ms
​ :crit, 16 00:00:56.030, 665ms
​ :16 00:00:56.695, 5681ms
​ :crit, 16 00:01:02.376, 767ms
​ :crit, 16 00:01:03.143, 345ms
​228 tests :16 00:01:03.488, 36375ms
​ :active, 16 00:01:39.863, 766ms
​ :16 00:01:40.629, 9875ms
​ :active, 16 00:01:50.504, 766ms
​ :16 00:01:51.270, 12609ms
​ :active, 16 00:02:03.879, 766ms
​ :16 00:02:04.645, 3641ms
​ :crit, 16 00:02:08.286, 767ms
​123 tests :16 00:02:09.053, 17687ms
​ :crit, 16 00:02:26.740, 766ms
​ :16 00:02:27.506, 5474ms
​ :active, 16 00:02:32.980, 767ms
​212 tests :16 00:02:33.747, 32420ms
​ :crit, 16 00:03:06.167, 767ms
​ :16 00:03:06.934, 5858ms
```

<sub>🟩 passed · 🟥 failed · 🟨 failed, then retried · 0:00 = 2026-10-21 08:16:56 UTC</sub>
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
<summary>Test timings (100 of 20103: failures, retries and the slowest)</summary>

| Start | Duration | Thread | Test | Status |
| ---: | ---: | :--- | :--- | :--- |
| 0:03.891 | 180ms | ForkJoinPool-1-worker-12 | suite 11 test 32 | ❌ failed |
| 0:04.543 | 196ms | ForkJoinPool-1-worker-12 | suite 11 test 40 | ❌ failed |
| 0:05.775 | 147ms | ForkJoinPool-1-worker-2 | suite 1 test 41 | ❌ failed |
| 0:06.432 | 563ms | ForkJoinPool-1-worker-5 | suite 4 test 35 | ❌ failed |
| 0:06.843 | 196ms | ForkJoinPool-1-worker-7 | suite 6 test 44 | ❌ failed |
| 0:09.438 | 102ms | ForkJoinPool-1-worker-11 | suite 10 test 58 | ❌ failed |
| 0:11.845 | 131ms | ForkJoinPool-1-worker-15 | suite 14 test 83 | ❌ failed |
| 0:13.648 | 110ms | ForkJoinPool-1-worker-5 | suite 4 test 82 | ❌ failed |
| 0:15.019 | 548ms | ForkJoinPool-1-worker-9 | suite 8 test 99 | ❌ failed |
| 0:15.908 | 238ms | ForkJoinPool-1-worker-11 | suite 10 test 105 | ❌ failed |
| 0:15.948 | 113ms | ForkJoinPool-1-worker-4 | suite 3 test 105 | ❌ failed |
| 0:16.053 | 396ms | ForkJoinPool-1-worker-6 | suite 5 test 89 | ❌ failed |
| 0:17.649 | 200ms | ForkJoinPool-1-worker-13 | suite 12 test 114 | ❌ failed |
| 0:18.771 | 182ms | ForkJoinPool-1-worker-11 | suite 10 test 120 | ❌ failed |
| 0:21.317 | 117ms | ForkJoinPool-1-worker-6 | suite 5 test 123 | ❌ failed |
| 0:23.778 | 241ms | ForkJoinPool-1-worker-5 | suite 4 test 151 | ❌ failed |
| 0:24.243 | 132ms | ForkJoinPool-1-worker-6 | suite 5 test 140 | ❌ failed |
| 0:24.559 | 156ms | ForkJoinPool-1-worker-3 | suite 2 test 164 | ❌ failed |
| 0:30.661 | 614ms | ForkJoinPool-1-worker-4 | suite 3 test 207 | ❌ failed |
| 0:32.116 | 150ms | ForkJoinPool-1-worker-10 | suite 9 test 196 | ❌ failed |
| 0:32.498 | 144ms | ForkJoinPool-1-worker-16 | suite 15 test 236 | ❌ failed |
| 0:36.229 | 146ms | ForkJoinPool-1-worker-14 | suite 13 test 246 | ❌ failed |
| 0:39.449 | 245ms | ForkJoinPool-1-worker-7 | suite 6 test 251 | ❌ failed |
| 0:43.746 | 260ms | ForkJoinPool-1-worker-4 | suite 3 test 290 | ❌ failed |
| 0:47.415 | 479ms | ForkJoinPool-1-worker-14 | suite 13 test 326 | ❌ failed |
| 0:50.738 | 132ms | ForkJoinPool-1-worker-10 | suite 9 test 320 | ❌ failed |
| 0:51.884 | 374ms | ForkJoinPool-1-worker-3 | suite 2 test 345 | ❌ failed |
| 0:55.557 | 179ms | ForkJoinPool-1-worker-16 | suite 15 test 375 | ❌ failed |
| 0:57.799 | 201ms | ForkJoinPool-1-worker-12 | suite 11 test 386 | ❌ failed |
| 0:58.117 | 407ms | ForkJoinPool-1-worker-6 | suite 5 test 372 | ❌ failed |
| 0:58.219 | 121ms | ForkJoinPool-1-worker-7 | suite 6 test 385 | ❌ failed |
| 0:58.847 | 167ms | ForkJoinPool-1-worker-1 | suite 0 test 418 | ❌ failed |
| 1:00.384 | 181ms | ForkJoinPool-1-worker-14 | suite 13 test 417 | ❌ failed |
| 1:03.914 | 136ms | ForkJoinPool-1-worker-13 | suite 12 test 434 | ❌ failed |
| 1:04.708 | 218ms | ForkJoinPool-1-worker-9 | suite 8 test 408 | ❌ failed |
| 1:06.015 | 145ms | ForkJoinPool-1-worker-8 | suite 7 test 417 | ❌ failed |
| 1:08.477 | 201ms | ForkJoinPool-1-worker-13 | suite 12 test 461 | ❌ failed |
| 1:10.301 | 168ms | ForkJoinPool-1-worker-2 | suite 1 test 506 | ❌ failed |
| 1:13.066 | 199ms | ForkJoinPool-1-worker-11 | suite 10 test 462 | ❌ failed |
| 1:15.810 | 131ms | ForkJoinPool-1-worker-1 | suite 0 test 538 | ❌ failed |
| 1:17.249 | 416ms | ForkJoinPool-1-worker-5 | suite 4 test 477 | ❌ failed |
| 1:17.917 | 139ms | ForkJoinPool-1-worker-7 | suite 6 test 508 | ❌ failed |
| 1:19.148 | 335ms | ForkJoinPool-1-worker-2 | suite 1 test 561 | ❌ failed |
| 1:20.365 | 193ms | ForkJoinPool-1-worker-6 | suite 5 test 501 | ❌ failed |
| 1:21.066 | 420ms | ForkJoinPool-1-worker-3 | suite 2 test 533 | ❌ failed |
| 1:21.507 | 395ms | ForkJoinPool-1-worker-4 | suite 3 test 543 | ❌ failed |
| 1:22.211 | 295ms | ForkJoinPool-1-worker-10 | suite 9 test 529 | ❌ failed |
| 1:26.650 | 128ms | ForkJoinPool-1-worker-13 | suite 12 test 567 | ❌ failed |
| 1:26.684 | 248ms | ForkJoinPool-1-worker-1 | suite 0 test 596 | ❌ failed |
| 1:33.295 | 101ms | ForkJoinPool-1-worker-5 | suite 4 test 569 | ❌ failed |
| 1:33.517 | 151ms | ForkJoinPool-1-worker-1 | suite 0 test 642 | ❌ failed |
| 1:39.871 | 181ms | ForkJoinPool-1-worker-13 | suite 12 test 657 | ❌ failed |
| 1:42.264 | 120ms | ForkJoinPool-1-worker-1 | suite 0 test 687 | ❌ failed |
| 1:48.469 | 276ms | ForkJoinPool-1-worker-5 | suite 4 test 658 | ❌ failed |
| 1:51.353 | 386ms | ForkJoinPool-1-worker-13 | suite 12 test 729 | ❌ failed |
| 1:55.264 | 150ms | ForkJoinPool-1-worker-7 | suite 6 test 771 | ❌ failed |
| 1:56.149 | 248ms | ForkJoinPool-1-worker-4 | suite 3 test 774 | ❌ failed |
| 1:56.510 | 157ms | ForkJoinPool-1-worker-11 | suite 10 test 749 | ❌ failed |
| 1:58.381 | 234ms | ForkJoinPool-1-worker-11 | suite 10 test 760 | ❌ failed |
| 1:58.465 | 99ms | ForkJoinPool-1-worker-15 | suite 14 test 806 | ❌ failed |
| 2:01.430 | 466ms | ForkJoinPool-1-worker-8 | suite 7 test 800 | ❌ failed |
| 2:01.966 | 138ms | ForkJoinPool-1-worker-10 | suite 9 test 783 | ❌ failed |
| 2:02.144 | 501ms | ForkJoinPool-1-worker-13 | suite 12 test 801 | ❌ failed |
| 2:04.712 | 196ms | ForkJoinPool-1-worker-1 | suite 0 test 843 | ❌ failed |
| 2:05.801 | 140ms | ForkJoinPool-1-worker-2 | suite 1 test 834 | ❌ failed |
| 2:10.043 | 148ms | ForkJoinPool-1-worker-7 | suite 6 test 875 | ❌ failed |
| 2:11.628 | 98ms | ForkJoinPool-1-worker-12 | suite 11 test 867 | ❌ failed |
| 2:11.634 | 200ms | ForkJoinPool-1-worker-10 | suite 9 test 861 | ❌ failed |
| 2:13.758 | 432ms | ForkJoinPool-1-worker-11 | suite 10 test 861 | ❌ failed |
| 2:14.292 | 220ms | ForkJoinPool-1-worker-14 | suite 13 test 909 | ❌ failed |
| 2:17.659 | 147ms | ForkJoinPool-1-worker-1 | suite 0 test 925 | ❌ failed |
| 2:18.001 | 182ms | ForkJoinPool-1-worker-6 | suite 5 test 898 | ❌ failed |
| 2:18.467 | 101ms | ForkJoinPool-1-worker-11 | suite 10 test 888 | ❌ failed |
| 2:21.789 | 107ms | ForkJoinPool-1-worker-6 | suite 5 test 926 | ❌ failed |
| 2:22.630 | 110ms | ForkJoinPool-1-worker-14 | suite 13 test 968 | ❌ failed |
| 2:25.515 | 127ms | ForkJoinPool-1-worker-7 | suite 6 test 971 | ❌ failed |
| 2:25.645 | 199ms | ForkJoinPool-1-worker-13 | suite 12 test 967 | ❌ failed |
| 2:27.207 | 120ms | ForkJoinPool-1-worker-6 | suite 5 test 963 | ❌ failed |
| 2:28.265 | 113ms | ForkJoinPool-1-worker-1 | suite 0 test 996 | ❌ failed |
| 2:28.395 | 160ms | ForkJoinPool-1-worker-3 | suite 2 test 990 | ❌ failed |
| 2:40.154 | 499ms | ForkJoinPool-1-worker-14 | suite 13 test 1071 | ❌ failed |
| 2:43.741 | 256ms | ForkJoinPool-1-worker-1 | suite 0 test 1095 | ❌ failed |
| 2:48.790 | 407ms | ForkJoinPool-1-worker-12 | suite 11 test 1082 | ❌ failed |
| 2:48.822 | 152ms | ForkJoinPool-1-worker-15 | suite 14 test 1134 | ❌ failed |
| 2:49.594 | 95ms | ForkJoinPool-1-worker-8 | suite 7 test 1072 | ❌ failed |
| 2:51.972 | 263ms | ForkJoinPool-1-worker-5 | suite 4 test 1109 | ❌ failed |
| 2:52.097 | 406ms | ForkJoinPool-1-worker-8 | suite 7 test 1092 | ❌ failed |
| 2:55.193 | 101ms | ForkJoinPool-1-worker-10 | suite 9 test 1147 | ❌ failed |
| 2:55.346 | 116ms | ForkJoinPool-1-worker-9 | suite 8 test 1131 | ❌ failed |
| 2:55.947 | 110ms | ForkJoinPool-1-worker-4 | suite 3 test 1176 | ❌ failed |
| 2:56.788 | 296ms | ForkJoinPool-1-worker-14 | suite 13 test 1190 | ❌ failed |
| 2:58.628 | 134ms | ForkJoinPool-1-worker-8 | suite 7 test 1130 | ❌ failed |
| 2:59.143 | 237ms | ForkJoinPool-1-worker-12 | suite 11 test 1150 | ❌ failed |
| 2:59.487 | 415ms | ForkJoinPool-1-worker-6 | suite 5 test 1164 | ❌ failed |
| 2:59.968 | 132ms | ForkJoinPool-1-worker-6 | suite 5 test 1166 | ❌ failed |
| 3:01.017 | 115ms | ForkJoinPool-1-worker-1 | suite 0 test 1203 | ❌ failed |
| 3:01.561 | 262ms | ForkJoinPool-1-worker-10 | suite 9 test 1189 | ❌ failed |
| 3:02.029 | 121ms | ForkJoinPool-1-worker-9 | suite 8 test 1169 | ❌ failed |
| 3:06.890 | 137ms | ForkJoinPool-1-worker-12 | suite 11 test 1192 | ❌ failed |
| 3:08.803 | 192ms | ForkJoinPool-1-worker-11 | suite 10 test 1223 | ❌ failed |

</details>

package com.mayank.framepulse.model

/**
 * Where the time of one drawn frame went, as the platform measured it
 * (`android.view.FrameMetrics`, API 24+). All values are in milliseconds.
 *
 * The stages run partly in parallel on the main thread, the render thread and the GPU, so they
 * do not add up to [JankFrame.durationMs]. Use them to see which stage was unusually long:
 * - [unknownDelayMs], [inputHandlingMs], [animationMs], [layoutMeasureMs] and [drawMs] are
 *   main-thread work. A long [unknownDelayMs] means the main thread was busy with something else
 *   when the frame should have started.
 * - [syncMs], [commandIssueMs] and [swapBuffersMs] are render-thread work.
 * - [gpuMs] is GPU work.
 *
 * @property unknownDelayMs Time waiting for the main thread to start the frame, for example
 *   because it was still running a previous message. Normally close to 0.
 * @property inputHandlingMs Time handling input events.
 * @property animationMs Time running animations.
 * @property layoutMeasureMs Time measuring and laying out the view hierarchy.
 * @property drawMs Time recording the view hierarchy's drawing commands (`View.draw`).
 * @property syncMs Time handing the recorded drawing over to the render thread. Many or large
 *   new bitmaps usually make this long.
 * @property commandIssueMs Time the render thread spent issuing drawing commands to the GPU.
 * @property swapBuffersMs Time handing the finished frame to the display system.
 * @property gpuMs Time the GPU spent on the frame. Null below API 31, where the platform does
 *   not report it.
 */
public data class FrameStages(
    val unknownDelayMs: Double,
    val inputHandlingMs: Double,
    val animationMs: Double,
    val layoutMeasureMs: Double,
    val drawMs: Double,
    val syncMs: Double,
    val commandIssueMs: Double,
    val swapBuffersMs: Double,
    val gpuMs: Double?,
)

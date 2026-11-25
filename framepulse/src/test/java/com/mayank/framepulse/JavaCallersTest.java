package com.mayank.framepulse;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.view.Display;

import org.junit.Test;

/** The public API as a Java caller sees it. */
public class JavaCallersTest {

    @Test
    public void configIsBuiltWithTheBuilder() {
        FramePulseConfig config = new FramePulseConfig.Builder()
                .windowDurationMs(2_000)
                .refreshRate(120f)
                .build();

        assertEquals(2_000L, config.getWindowDurationMs());
        assertEquals(8.3333f, config.getExpectedFramePeriodMs(), 0.0001f);
        assertEquals(FramePulseConfig.DEFAULT_REPORT_INTERVAL_MS, config.getReportIntervalMs());
        assertEquals(FramePulseConfig.DEFAULT, new FramePulseConfig.Builder().build());
    }

    /**
     * Never run, since starting a session needs the main thread. It compiles only if Java can
     * call every `start` overload statically, without `FramePulse.INSTANCE`, and read the
     * results.
     */
    @SuppressWarnings("unused")
    private static void everyStartOverloadCompiles(
            Activity activity, Context context, Display display, FramePulseConfig config) {
        FramePulseListener listener = metrics -> {
            double fps = metrics.getFps();
            long frozen = metrics.getTotalFrozenFrameCount();
        };
        JankFrameListener jankFrameListener = frame -> {
            boolean frozen = frame.isFrozen();
            FrameStages stages = frame.getStages();
            String screen = frame.getTags().get("screen");
        };

        FramePulseSession session = FramePulse.start(activity, metrics -> { });
        session = FramePulse.start(activity, config, listener);
        session = FramePulse.start(activity, config, jankFrameListener, listener);
        session = FramePulse.start(listener);
        session = FramePulse.start(config, listener);
        session = FramePulse.start(config, jankFrameListener, listener);
        session = FramePulse.start(context, display, listener);
        session = FramePulse.start(context, display, config, listener);
        session = FramePulse.start(context, display, config, jankFrameListener, listener);

        session.setTag("screen", "Feed");
        session.removeTag("screen");
        SessionSummary summary = session.summary();
        double jankRate = summary.getJankRate();
        for (TagSummary tag : summary.getTags()) {
            long frames = tag.getFrameCount();
        }
        boolean running = session.isRunning();
        session.stop();
        long frozenThresholdMs = FramePulseMetrics.FROZEN_FRAME_THRESHOLD_MS;
    }

    /** Never run, like the method above: Java can call every monitorActivities overload. */
    @SuppressWarnings("unused")
    private static void everyMonitorActivitiesOverloadCompiles(
            Application application, Activity activity, FramePulseConfig config) {
        ActivitySessionListener listener = (stopped, summary) -> {
            String name = stopped.getClass().getSimpleName();
            long jank = summary.getJankCount();
        };
        JankFrameListener jankFrameListener = frame -> {
            String screen = frame.getTags().get(FramePulse.ACTIVITY_TAG);
        };

        ActivityMonitor monitor = FramePulse.monitorActivities(application, listener);
        monitor = FramePulse.monitorActivities(application, config, listener);
        monitor = FramePulse.monitorActivities(application, config, jankFrameListener, listener);

        FramePulseSession session = monitor.sessionFor(activity);
        monitor.stop();
    }
}

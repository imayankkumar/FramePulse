package com.mayank.framepulse.gradle

import org.gradle.api.provider.Property

/**
 * The `framepulse { }` block:
 *
 * ```kotlin
 * framepulse {
 *     port = 9000
 * }
 * ```
 */
abstract class FramePulseExtension {

    /**
     * Adds `framepulse-report` to `debugImplementation`. Default true. Set it to false when the
     * module adds the report itself, for example as a project dependency.
     */
    abstract val addReportDependency: Property<Boolean>

    /** The localhost port `framepulseReport` tries first. Default 8765; the next free one is used. */
    abstract val port: Property<Int>
}

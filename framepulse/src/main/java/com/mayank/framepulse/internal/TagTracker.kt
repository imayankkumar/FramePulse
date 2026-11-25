package com.mayank.framepulse.internal

import com.mayank.framepulse.TagSummary

/**
 * Remembers when each tag was set and removed, so a frame can be given the tags that applied
 * while it ran.
 *
 * Frames are reported after they happen (drawn frames come a little later from the render
 * thread), so the tags at report time can already differ from the tags during the frame. A tag
 * therefore belongs to a frame when it was set at any time during the frame:
 * `added <= frameEnd` and (still set, or `removed >= frameStart`).
 *
 * Removed tags are kept until [forgetRemovedBefore] passes their removal time, and at most
 * [maxRemovedEntries] of them, so memory stays bounded while no frames arrive.
 *
 * Each tag value also keeps session counters ([countFrame], [summaries]) for the whole session,
 * for at most [maxCountedValues] distinct name and value pairs. Values set after that limit
 * still appear on jank frames but are not counted.
 *
 * All times are in the `System.nanoTime()` time base, like frame times.
 *
 * Threading: not thread-safe. Main thread only.
 */
internal class TagTracker(
    private val maxRemovedEntries: Int = MAX_REMOVED_ENTRIES,
    private val maxCountedValues: Int = MAX_COUNTED_VALUES,
) {

    /** Session counters for one name and value pair. */
    private class Counters(val name: String, val value: String) {
        var frames = 0L
        var janks = 0L
        var frozen = 0L

        /** The [frameNumber] last counted, so overlapping entries count a frame once. */
        var lastFrame = -1L
    }

    private class Entry(
        val name: String,
        val value: String,
        val addedNanos: Long,
        val counters: Counters?,
    ) {
        var removedNanos: Long = STILL_SET
        val isRemoved: Boolean get() = removedNanos != STILL_SET
    }

    // Oldest first. At most one entry per name is still set.
    private val entries = ArrayList<Entry>()
    private var removedCount = 0

    // Counters by name, then by value, in the order they were first set.
    private val counters = LinkedHashMap<String, LinkedHashMap<String, Counters>>()
    private var counterCount = 0
    private var frameNumber = 0L

    /** Sets [name] to [value]. Setting the value a tag already has changes nothing. */
    fun set(name: String, value: String, nowNanos: Long) {
        val current = currentEntry(name)
        if (current != null) {
            if (current.value == value) return
            markRemoved(current, nowNanos)
        }
        entries += Entry(name, value, nowNanos, countersFor(name, value))
        trimRemoved()
    }

    /** Removes [name]. Removing a tag that is not set changes nothing. */
    fun remove(name: String, nowNanos: Long) {
        val current = currentEntry(name) ?: return
        markRemoved(current, nowNanos)
        trimRemoved()
    }

    /**
     * The tags that were set at any time between [startNanos] and [endNanos]. When a tag
     * changed value during that time, its newest value wins. Allocates only when a tag applies.
     */
    fun tagsDuring(startNanos: Long, endNanos: Long): Map<String, String> {
        var result: LinkedHashMap<String, String>? = null
        for (i in entries.indices) {
            val entry = entries[i]
            if (entry.addedNanos > endNanos) continue
            if (entry.isRemoved && entry.removedNanos < startNanos) continue
            val tags = result ?: LinkedHashMap<String, String>().also { result = it }
            tags[entry.name] = entry.value
        }
        return result ?: emptyMap()
    }

    /**
     * Counts one frame that ran from [startNanos] to [endNanos] for every tag value that was set
     * during it. Does not allocate.
     */
    fun countFrame(startNanos: Long, endNanos: Long, isJank: Boolean, isFrozen: Boolean) {
        val frame = frameNumber++
        for (i in entries.indices) {
            val entry = entries[i]
            if (entry.addedNanos > endNanos) continue
            if (entry.isRemoved && entry.removedNanos < startNanos) continue
            val counted = entry.counters ?: continue
            if (counted.lastFrame == frame) continue
            counted.lastFrame = frame
            counted.frames++
            if (isJank) counted.janks++
            if (isFrozen) counted.frozen++
        }
    }

    /** One summary per counted name and value pair, in the order they were first set. */
    fun summaries(): List<TagSummary> {
        val result = ArrayList<TagSummary>(counterCount)
        for (byValue in counters.values) {
            for (counted in byValue.values) {
                result += TagSummary(
                    name = counted.name,
                    value = counted.value,
                    frameCount = counted.frames,
                    jankCount = counted.janks,
                    frozenFrameCount = counted.frozen,
                )
            }
        }
        return result
    }

    /**
     * Drops tags removed before [nanos]. Call with the start of the newest frame: later frames
     * start later, so those tags can no longer apply to any of them.
     */
    fun forgetRemovedBefore(nanos: Long) {
        if (removedCount == 0) return
        var i = entries.size - 1
        while (i >= 0) {
            val entry = entries[i]
            if (entry.isRemoved && entry.removedNanos < nanos) {
                entries.removeAt(i)
                removedCount--
            }
            i--
        }
    }

    private fun currentEntry(name: String): Entry? {
        for (i in entries.indices) {
            val entry = entries[i]
            if (!entry.isRemoved && entry.name == name) return entry
        }
        return null
    }

    private fun countersFor(name: String, value: String): Counters? {
        val byValue = counters[name]
        byValue?.get(value)?.let { return it }
        if (counterCount >= maxCountedValues) return null
        val created = Counters(name, value)
        (byValue ?: LinkedHashMap<String, Counters>().also { counters[name] = it })[value] = created
        counterCount++
        return created
    }

    private fun markRemoved(entry: Entry, nowNanos: Long) {
        entry.removedNanos = nowNanos
        removedCount++
    }

    /** Keeps at most [maxRemovedEntries] removed tags, dropping the oldest first. */
    private fun trimRemoved() {
        var i = 0
        while (removedCount > maxRemovedEntries && i < entries.size) {
            if (entries[i].isRemoved) {
                entries.removeAt(i)
                removedCount--
            } else {
                i++
            }
        }
    }

    private companion object {
        const val STILL_SET = Long.MIN_VALUE
        const val MAX_REMOVED_ENTRIES = 64
        const val MAX_COUNTED_VALUES = 256
    }
}

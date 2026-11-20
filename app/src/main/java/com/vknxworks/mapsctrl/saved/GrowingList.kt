package com.vknxworks.mapsctrl.saved

/**
 * A list that only grows at its end, for a track being recorded. [add] gives back a read-only list of everything
 * added so far without copying it: every such list shares this buffer and has its own fixed size, so it never
 * changes after it was handed out. (Copying the whole list for every new point made a long recording slower and
 * slower.)
 */
class GrowingList<T> {
    private val items = ArrayList<T>()

    val size: Int get() = synchronized(items) { items.size }

    fun lastOrNull(): T? = synchronized(items) { items.lastOrNull() }

    /** Adds [item] and returns the list as it is now. */
    fun add(item: T): List<T> = synchronized(items) {
        items.add(item)
        Snapshot(this, items.size)
    }

    private fun at(index: Int): T = synchronized(items) { items[index] }

    /** The first [size] items of [owner]; they never change, because the buffer only grows. */
    private class Snapshot<T>(private val owner: GrowingList<T>, override val size: Int) : AbstractList<T>() {
        override fun get(index: Int): T {
            if (index < 0 || index >= size) throw IndexOutOfBoundsException("index $index, size $size")
            return owner.at(index)
        }

        // Two snapshots of the same buffer are equal exactly when they have the same size: no need to compare
        // every point (which is what makes a state holder's "did it change?" check cheap).
        override fun equals(other: Any?): Boolean = when {
            other === this -> true
            other is Snapshot<*> && other.owner === owner -> other.size == size
            else -> super.equals(other)
        }

        override fun hashCode(): Int = super.hashCode()
    }
}

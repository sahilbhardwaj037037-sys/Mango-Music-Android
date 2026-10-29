package com.mangomusic.playback

import androidx.media3.exoplayer.source.ShuffleOrder

/** Stable traversal; new entries start at its tail until Mix reapplies manual priority. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class ExplicitShuffleOrder(private val order: List<Int>, private val random: kotlin.random.Random=kotlin.random.Random.Default): ShuffleOrder {
    override fun getLength()=order.size
    override fun getFirstIndex()=order.firstOrNull() ?: -1
    override fun getLastIndex()=order.lastOrNull() ?: -1
    override fun getNextIndex(index: Int)=order.indexOf(index).let { if(it<0) -1 else order.getOrNull(it+1) ?: -1 }
    override fun getPreviousIndex(index: Int)=order.indexOf(index).let { if(it<0) -1 else order.getOrNull(it-1) ?: -1 }
    override fun cloneAndInsert(insertionIndex: Int,insertionCount: Int): ShuffleOrder = ExplicitShuffleOrder(
        order.map { if(it>=insertionIndex) it+insertionCount else it }+(insertionIndex until insertionIndex+insertionCount).shuffled(random),random)
    override fun cloneAndRemove(indexFrom: Int,indexToExclusive: Int): ShuffleOrder = ExplicitShuffleOrder(
        order.filter { it !in indexFrom until indexToExclusive }.map { if(it>=indexToExclusive) it-(indexToExclusive-indexFrom) else it },random)
    override fun cloneAndClear(): ShuffleOrder = ExplicitShuffleOrder(emptyList(),random)
}

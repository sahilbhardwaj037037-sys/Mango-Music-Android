package com.mangomusic

import com.mangomusic.playback.ExplicitShuffleOrder
import androidx.media3.exoplayer.source.ShuffleOrder
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ShuffleOrderTest {
    private fun traversal(order:ShuffleOrder):List<Int> {
        val out=mutableListOf<Int>();var i=order.firstIndex
        while(i>=0 && out.size<order.length) { out+=i;i=order.getNextIndex(i) }
        return out
    }
    @Test fun newBatchIsShuffledWithoutReorderingExistingTraversal() {
        val order=ExplicitShuffleOrder(listOf(2,0,1),Random(0)).cloneAndInsert(1,5)
        val walked=traversal(order)
        assertEquals(listOf(7,0,6),walked.take(3))
        assertEquals((1..5).toSet(),walked.drop(3).toSet())
        assertNotEquals((1..5).toList(),walked.drop(3))
        assertEquals((0..7).toSet(),walked.toSet())
    }
    @Test fun removalKeepsPreviousNavigationConsistent() {
        val order=ExplicitShuffleOrder(listOf(3,0,4,1,2)).cloneAndRemove(1,3)
        val walked=traversal(order)
        assertEquals(listOf(1,0,2),walked)
        assertEquals(-1,order.getPreviousIndex(walked.first()))
        walked.zipWithNext().forEach { (before,after) -> assertEquals(before,order.getPreviousIndex(after)) }
        assertEquals(-1,order.getNextIndex(walked.last()))
    }
}

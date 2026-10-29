package com.mangomusic

import com.mangomusic.domain.*
import org.junit.Assert.*
import org.junit.Test

class MixPolicyTest {
    private fun song(id:String,title:String="Song $id",artist:String="Artist")=Track("youtube:$id",title,artist,"https://www.youtube.com/watch?v=$id",180000)
    @Test fun rejectsQueuedRecentAndRepeatedRecordings() {
        val played=song("abcdefghij1","Same Song")
        val duplicateUpload=song("abcdefghij2","Same Song (Official Audio)")
        val fresh=song("abcdefghij3")
        assertEquals(listOf(fresh),MixPolicy.select(listOf(played,duplicateUpload,fresh,fresh),listOf(played),emptySet()))
    }
    @Test fun limitsBatchAndRejectsInvalidOrLongEntries() {
        val good=(1..6).map { song("abcdefghij$it") }
        assertEquals(good.take(3),MixPolicy.select(listOf(Track("bad","Invalid","",""),song("abcdefghij9").copy(durationMs=3600000))+good,emptyList(),emptySet()))
    }
    @Test fun respectsRejectedIdsEvenWithoutHistory() {
        val candidate=song("abcdefghij1")
        assertTrue(MixPolicy.select(listOf(candidate),emptyList(),setOf(candidate.id)).isEmpty())
    }
    @Test fun songKeyHandlesAccentsArtistPrefixAndMetadataNoise() {
        assertEquals(MixPolicy.songKey(song("abcdefghij1","Artist - Càlm Song (Official Video)")),MixPolicy.songKey(song("abcdefghij2","Calm Song")))
    }
    @Test fun shuffleKeepsHistoryThenManualPlayNextThenMix() {
        // 4 and 2 are recommendations. Index 5 was explicitly requested as Play Next.
        assertEquals(listOf(3,0,5,1,4,2),MixPolicy.prioritize(listOf(3,0,4,1,2,5),0,setOf(4,2),mapOf(5 to 100L)))
    }
    @Test fun preservesManualShuffleOrderAndDoesNotRevisitPrefix() {
        assertEquals(listOf(2,4,0,3,1),MixPolicy.prioritize(listOf(2,4,0,1,3),4,setOf(1)))
        assertEquals(listOf(0,1),MixPolicy.prioritize(listOf(0,1),-1,setOf(1)))
    }
    @Test fun recognizesArtistCreditAcrossDifferentUploadersInEitherDirection() {
        val original=song("abcdefghij1","Carefree","Kevin MacLeod").copy(durationMs=205000)
        val upload=song("abcdefghij2","Carefree – Kevin MacLeod (No Copyright Music)","Audio Library").copy(durationMs=205000)
        assertTrue(MixPolicy.select(listOf(upload),listOf(original),emptySet()).isEmpty())
        assertTrue(MixPolicy.select(listOf(original),listOf(upload),emptySet()).isEmpty())
    }
    @Test fun doesNotMergeDistinctVersionsOrSameTitleWithDifferentDurations() {
        val original=song("abcdefghij1","Carefree","Kevin MacLeod")
        val remix=song("abcdefghij2","Kevin MacLeod - Carefree Remix","Other channel")
        val other=song("abcdefghij3","Carefree","Another artist").copy(durationMs=240000)
        assertEquals(listOf(remix,other),MixPolicy.select(listOf(remix,other),listOf(original),emptySet()))
    }
}

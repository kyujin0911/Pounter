package io.github.kyujin0911.pounter

import io.github.kyujin0911.pounter.data.detector.*
import org.junit.Assert.*
import org.junit.Test

class CandidateMergeTest {
    private val policy = MergePolicy(.25, .4, .9)

    @Test
    fun nestedRimsMergeOnce() {
        val outer = candidate(50.0, 50.0)
        val inner =
            candidate(50.0, 50.0, 15.0)
                .copy(sources = setOf(CandidateSource.REGION), edgeSupport = .8)
        val merged = mergeCandidates(listOf(inner, outer), policy)
        assertEquals(1, merged.size)
        assertEquals(20.0, merged.single().semiMinorPx, 0.0)
        assertEquals(
            setOf(CandidateSource.CONTOUR, CandidateSource.REGION),
            merged.single().sources,
        )
        assertEquals(merged, mergeCandidates(listOf(outer, inner), policy))
    }

    @Test
    fun neighboringPipesRemainSeparate() {
        assertEquals(
            2,
            mergeCandidates(listOf(candidate(50.0, 50.0), candidate(80.0, 50.0)), policy).size,
        )
    }

    @Test
    fun noTransitiveNeighborMerge() {
        val a = candidate(50.0, 50.0, 20.0)
        val b = candidate(54.0, 50.0, 16.0).copy(edgeSupport = .8)
        val c = candidate(58.0, 50.0, 12.0).copy(edgeSupport = .7)
        assertEquals(2, mergeCandidates(listOf(c, b, a), MergePolicy(.5, .5, .9)).size)
    }
}

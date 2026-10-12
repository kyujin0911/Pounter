package com.example.pipecounter

import com.example.pipecounter.model.*
import org.junit.Assert.*
import org.junit.Test

class ModelTest {
    private fun task()=CountTask("one","one.jpg",1000,1500,1L,analyzed=true)
    @Test fun editUndoAndConfirmPreserveCount() {
        val a=Marker("a",.3f,.4f,.02f,true)
        val b=Marker("b",.6f,.4f,.02f,true)
        val first=task().edit(listOf(a))
        val second=first.edit(listOf(a,b))
        assertEquals(2,second.count)
        assertEquals(first.markers,second.undoEdit().markers)
        val confirmed=second.confirm(100)
        assertEquals(2,confirmed.count)
        assertTrue(confirmed.confirmed)
        assertNull(confirmed.undo)
    }
    @Test(expected=IllegalStateException::class) fun confirmedCannotBeEdited() {task().confirm(2).edit(emptyList())}
    @Test(expected=IllegalStateException::class) fun unreviewedCannotBeConfirmed() {task().copy(analyzed=false).confirm(2)}
    @Test(expected=IllegalArgumentException::class) fun markerOutsideRegionRejected() {
        task().copy(region=Region(.2f,.2f,.8f,.8f)).edit(listOf(Marker("a",.1f,.5f,.02f)))
    }
    @Test fun regionResetRemovesStaleResultsAndUndo() {
        val m=Marker("a",.3f,.4f,.02f)
        val reset=task().copy(automatic=listOf(m),markers=listOf(m),undo=emptyList(),analysisMillis=12).reset(Region(.1f,.1f,.9f,.9f))
        assertFalse(reset.analyzed);assertTrue(reset.markers.isEmpty());assertTrue(reset.automatic.isEmpty());assertNull(reset.undo);assertNull(reset.analysisMillis)
    }
    @Test fun concentricEdgesMergeButAdjacentPipesRemain() {
        val outer=Marker("outer",.3f,.4f,.05f)
        val inner=Marker("inner",.302f,.4f,.04f)
        val adjacent=Marker("next",.39f,.4f,.05f)
        assertEquals(setOf("outer","next"),suppressConcentric(listOf(inner,adjacent,outer),1000,1500).map {it.id}.toSet())
    }
    @Test fun transformRoundTripsAcrossZoomPanAndLetterbox() {
        val g=PhotoGeometry(1000,1500,400f,340f,3f,40f,-60f)
        val screen=g.screenPoint(.6f,.5f)
        val image=g.imagePoint(screen.first,screen.second)!!
        assertEquals(.6f,image.first,.0001f); assertEquals(.5f,image.second,.0001f)
        assertNull(PhotoGeometry(1000,1500,400f,340f).imagePoint(0f,100f))
        assertEquals(0f,PhotoGeometry(1000,1500,400f,340f).clampPan(500f,500f).first,0f)
    }
}

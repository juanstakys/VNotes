package app.vnotes
import org.junit.Test
import org.junit.Assert.*

class SwipeNavigationTest {
    @Test fun leftInTodoAlwaysNavigatesAndNeverEdits(){
        assertEquals(ScreenSwipe.DONE,screenSwipe(false,-100f,10f,80f))
        assertFalse(completesNote(false,-100f,10f,80f))
    }
    @Test fun rightInDoneNavigatesRatherThanRestoringOrErasing(){
        assertEquals(ScreenSwipe.TODO,screenSwipe(true,100f,10f,80f))
        assertFalse(completesNote(true,100f,10f,80f))
    }
    @Test fun rightInTodoStillCompletesTheNote(){
        assertEquals(ScreenSwipe.NONE,screenSwipe(false,100f,10f,80f))
        assertTrue(completesNote(false,100f,10f,80f))
    }
    @Test fun leftInDoneHasNoDestructiveAction(){
        assertEquals(ScreenSwipe.NONE,screenSwipe(true,-100f,10f,80f))
        assertFalse(completesNote(true,-100f,10f,80f))
    }
    @Test fun verticalScrollAndSmallMovementsDoNotNavigate(){
        for(inDone in listOf(false,true))for(dx in listOf(-100f,100f)){
            assertEquals(ScreenSwipe.NONE,screenSwipe(inDone,dx,150f,80f))
            assertFalse(completesNote(inDone,dx,150f,80f))
            assertEquals(ScreenSwipe.NONE,screenSwipe(inDone,dx/2,0f,80f))
        }
    }
}

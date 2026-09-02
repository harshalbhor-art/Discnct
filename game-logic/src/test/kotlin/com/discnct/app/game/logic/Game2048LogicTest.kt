package com.discnct.app.game.logic

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class Game2048LogicTest {
    @Test fun `mergeRowLeft merges equal adjacent tiles once per pair`() {
        val (merged, moved) = mergeRowLeft(listOf(2, 2, 2, 2))
        assertEquals(listOf(4, 4, 0, 0), merged)
        assertTrue(moved)
    }

    @Test fun `mergeRowLeft does not merge a tile twice in one move`() {
        val (merged, _) = mergeRowLeft(listOf(2, 2, 2, 0))
        assertEquals(listOf(4, 2, 0, 0), merged)
    }

    @Test fun `mergeRowLeft reports no movement when already left-packed and unmergeable`() {
        val (merged, moved) = mergeRowLeft(listOf(4, 2, 0, 0))
        assertEquals(listOf(4, 2, 0, 0), merged)
        assertFalse(moved)
    }

    @Test fun `moveRight2048 mirrors moveLeft2048`() {
        val board = listOf(
            listOf(2, 2, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0),
        )
        val (result, moved) = moveRight2048(board)
        assertEquals(listOf(0, 0, 0, 4), result[0])
        assertTrue(moved)
    }

    @Test fun `moveUp2048 and moveDown2048 operate along columns`() {
        val board = listOf(
            listOf(2, 0, 0, 0),
            listOf(2, 0, 0, 0),
            listOf(0, 0, 0, 0),
            listOf(0, 0, 0, 0),
        )
        val (up, _) = moveUp2048(board)
        assertEquals(4, up[0][0])
        assertEquals(0, up[1][0])

        val (down, _) = moveDown2048(board)
        assertEquals(4, down[3][0])
        assertEquals(0, down[2][0])
    }

    @Test fun `canMove2048 is false when no direction changes the board`() {
        val stuck = listOf(
            listOf(2, 4, 2, 4),
            listOf(4, 2, 4, 2),
            listOf(2, 4, 2, 4),
            listOf(4, 2, 4, 2),
        )
        assertFalse(canMove2048(stuck))
    }

    @Test fun `spawnRandomTile2048 adds exactly one tile of value 2 or 4`() {
        val before = emptyBoard2048()
        val after = spawnRandomTile2048(before)
        val beforeCount = before.flatten().count { it != 0 }
        val afterCount = after.flatten().count { it != 0 }
        assertEquals(beforeCount + 1, afterCount)
        val newValue = after.flatten().first { it != 0 }
        assertTrue(newValue == 2 || newValue == 4)
    }

    @Test fun `rewardForMaxTile2048 scales with the highest tile reached`() {
        assertEquals(7, rewardForMaxTile2048(512))
        assertEquals(6, rewardForMaxTile2048(256))
        assertEquals(5, rewardForMaxTile2048(128))
        assertEquals(4, rewardForMaxTile2048(64))
        assertEquals(3, rewardForMaxTile2048(32))
        assertEquals(1, rewardForMaxTile2048(16))
    }
}

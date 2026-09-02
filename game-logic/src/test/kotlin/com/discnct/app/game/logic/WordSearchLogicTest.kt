package com.discnct.app.game.logic

import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class WordSearchLogicTest {

    @Test fun `every placed word really is spelled out in the grid it came back in`() {
        // The failure this catches is invisible on screen: a word listed as hidden that the letters
        // don't actually spell, because a later placement overwrote part of it.
        repeat(300) { seed ->
            val grid = randomWordSearch(random = Random(seed))
            for (placement in grid.placements) {
                val spelled = placement.cells.map { grid.letterAt(it.row, it.col) }.joinToString("")
                assertEquals(placement.word, spelled, "seed $seed: ${placement.word} isn't in the grid")
            }
        }
    }

    @Test fun `no word ever runs off the edge`() {
        repeat(300) { seed ->
            val grid = randomWordSearch(random = Random(seed))
            for (placement in grid.placements) {
                for (cell in placement.cells) {
                    assertTrue(
                        cell.row in 0 until grid.size && cell.col in 0 until grid.size,
                        "seed $seed: ${placement.word} leaves the grid at $cell",
                    )
                }
            }
        }
    }

    @Test fun `the grid is completely filled and the right size`() {
        val grid = randomWordSearch(random = Random(7))
        assertEquals(WORD_SEARCH_SIZE * WORD_SEARCH_SIZE, grid.letters.size)
        assertTrue(grid.letters.none { it == ' ' }, "a blank square would read as a hole")
        assertTrue(grid.letters.all { it in 'A'..'Z' })
    }

    @Test fun `all six words fit on a nine by nine grid, every time`() {
        // If the bank ever grows a word too long for the grid this is what says so.
        repeat(300) { seed ->
            val grid = randomWordSearch(random = Random(seed))
            assertEquals(WORD_SEARCH_WORDS, grid.placements.size, "seed $seed dropped a word")
        }
    }

    @Test fun `a word longer than the grid is dropped rather than forced`() {
        val grid = generateWordSearch(listOf("EXTRAORDINARY", "CALM"), size = 5, random = Random(1))
        assertEquals(listOf("CALM"), grid.words)
    }

    @Test fun `the same seed gives the same puzzle`() {
        assertEquals(randomWordSearch(random = Random(42)), randomWordSearch(random = Random(42)))
    }

    @Test fun `a straight run of squares is a path and a crooked one is not`() {
        assertEquals(
            listOf(Cell(2, 0), Cell(2, 1), Cell(2, 2)),
            wordSearchPath(Cell(2, 0), Cell(2, 2)),
        )
        assertEquals(
            listOf(Cell(0, 0), Cell(1, 1), Cell(2, 2)),
            wordSearchPath(Cell(0, 0), Cell(2, 2)),
        )
        assertNull(wordSearchPath(Cell(0, 0), Cell(1, 2)), "a knight's move is not a word")
        assertNull(wordSearchPath(Cell(3, 3), Cell(3, 3)), "one square is not a word either")
    }

    @Test fun `a path may run in any of the eight directions`() {
        assertEquals(3, wordSearchPath(Cell(4, 4), Cell(4, 2))?.size)
        assertEquals(3, wordSearchPath(Cell(4, 4), Cell(2, 4))?.size)
        assertEquals(3, wordSearchPath(Cell(4, 4), Cell(2, 6))?.size)
        assertEquals(3, wordSearchPath(Cell(4, 4), Cell(6, 2))?.size)
    }

    @Test fun `selecting a hidden word finds it, forwards or backwards`() {
        val grid = randomWordSearch(random = Random(11))
        val placement = grid.placements.first()
        val first = placement.cells.first()
        val last = placement.cells.last()
        assertEquals(placement, wordSearchMatch(grid, first, last))
        assertEquals(placement, wordSearchMatch(grid, last, first), "reading it the other way still counts")
    }

    @Test fun `selecting letters that spell nothing finds nothing`() {
        val grid = randomWordSearch(random = Random(11))
        assertNull(wordSearchMatch(grid, Cell(0, 0), Cell(1, 2)), "not even a straight line")
    }

    @Test fun `a selection running off the grid matches nothing rather than crashing`() {
        val grid = randomWordSearch(random = Random(3))
        assertNull(wordSearchMatch(grid, Cell(0, 0), Cell(0, grid.size + 3)))
        assertNull(wordSearchMatch(grid, Cell(0, 0), Cell(-4, 0)))
    }

    @Test fun `every word in the bank can be selected out of the grid it was hidden in`() {
        val grid = randomWordSearch(random = Random(99))
        for (placement in grid.placements) {
            assertNotNull(
                wordSearchMatch(grid, placement.cells.first(), placement.cells.last()),
                "${placement.word} is hidden but not findable",
            )
        }
    }

    @Test fun `finding everything pays most, finding some pays something, finding none still pays`() {
        assertEquals(WORD_SEARCH_FULL_REWARD, wordSearchReward(6, 6))
        assertTrue(wordSearchReward(3, 6) in 2 until WORD_SEARCH_FULL_REWARD)
        assertEquals(1, wordSearchReward(0, 6))
        assertEquals(1, wordSearchReward(0, 0), "an empty puzzle can't divide by zero")
    }

    @Test fun `finding more than there is to find is not worth more`() {
        assertEquals(WORD_SEARCH_FULL_REWARD, wordSearchReward(99, 6))
    }
}

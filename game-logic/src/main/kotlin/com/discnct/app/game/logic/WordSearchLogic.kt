package com.discnct.app.game.logic

import kotlin.random.Random

/**
 * Word search: the grid, the hiding, and the finding.
 *
 * All of it is here rather than in the composable, for the usual reason the other games have their
 * logic extracted — generation is the part that can silently go wrong (a word placed off the edge,
 * two words disagreeing about a shared square, a word that never made it in at all), and none of
 * that is visible from looking at a screen full of letters. Given a seeded [Random] it is fully
 * deterministic, so the tests can generate a few thousand grids and check every one.
 */

/** A square in the grid, row and column both zero-based from the top left. */
data class Cell(val row: Int, val col: Int)

/** A word and exactly which squares it occupies, in reading order. */
data class WordPlacement(val word: String, val cells: List<Cell>)

/**
 * A finished puzzle: the letters, and the answers.
 *
 * [placements] is the truth about what is hidden in [letters] — a word that couldn't be fitted is
 * absent from both, so the puzzle never asks for something that isn't there.
 */
data class WordSearchGrid(
    val size: Int,
    val letters: List<Char>,
    val placements: List<WordPlacement>,
) {
    fun letterAt(row: Int, col: Int): Char = letters[row * size + col]

    val words: List<String> get() = placements.map { it.word }
}

/** The eight directions a word may run, including backwards and both diagonals. */
private val DIRECTIONS = listOf(
    0 to 1, 0 to -1, 1 to 0, -1 to 0,
    1 to 1, 1 to -1, -1 to 1, -1 to -1,
)

/** Short, plain words. Long ones crowd a grid small enough to tap on a phone. */
val WORD_SEARCH_BANK: List<String> = listOf(
    "BREATH", "QUIET", "PAUSE", "SLEEP", "FOCUS", "CALM", "STILL", "REST",
    "WALK", "LIGHT", "CLEAR", "SLOW", "AWAKE", "PRESENT", "SPACE", "OUTSIDE",
    "MORNING", "RIVER", "STONE", "GARDEN", "PATIENT", "SIMPLE", "STEADY", "HANDS",
)

const val WORD_SEARCH_SIZE = 9
const val WORD_SEARCH_WORDS = 6

/** How many placements to try per word before giving up on it and moving on. */
private const val PLACEMENT_ATTEMPTS = 200

/**
 * Build a puzzle hiding [words] in a [size]×[size] grid.
 *
 * Longest first, because a long word has the fewest places it can go and fitting it around the
 * short ones is much harder than the other way round. Overlaps are allowed where the letters agree,
 * which is what stops the grid reading as a list.
 *
 * A word that won't fit after [PLACEMENT_ATTEMPTS] tries is dropped rather than forced. Dropping it
 * costs the player nothing — they were never told to look for it — while forcing it would either
 * corrupt a word already placed or loop forever on a grid too small for the bank it was given.
 */
fun generateWordSearch(
    words: List<String>,
    size: Int = WORD_SEARCH_SIZE,
    random: Random = Random.Default,
): WordSearchGrid {
    require(size > 0) { "grid size must be positive" }
    val letters = MutableList(size * size) { ' ' }
    val placements = ArrayList<WordPlacement>()

    for (word in words.map { it.uppercase() }.filter { it.isNotEmpty() }.sortedByDescending { it.length }) {
        if (word.length > size) continue
        var placed = false
        var attempt = 0
        while (attempt < PLACEMENT_ATTEMPTS && !placed) {
            attempt++
            val (dr, dc) = DIRECTIONS[random.nextInt(DIRECTIONS.size)]
            val row = random.nextInt(size)
            val col = random.nextInt(size)
            val cells = word.indices.map { Cell(row + dr * it, col + dc * it) }
            if (cells.any { it.row !in 0 until size || it.col !in 0 until size }) continue
            val fits = word.indices.all { index ->
                val cell = cells[index]
                val existing = letters[cell.row * size + cell.col]
                existing == ' ' || existing == word[index]
            }
            if (!fits) continue
            word.forEachIndexed { index, char -> letters[cells[index].row * size + cells[index].col] = char }
            placements.add(WordPlacement(word, cells))
            placed = true
        }
    }

    for (index in letters.indices) {
        if (letters[index] == ' ') letters[index] = ('A' + random.nextInt(26))
    }
    return WordSearchGrid(size, letters, placements)
}

/** A puzzle from [WORD_SEARCH_BANK]: [count] words picked at random. */
fun randomWordSearch(
    count: Int = WORD_SEARCH_WORDS,
    size: Int = WORD_SEARCH_SIZE,
    random: Random = Random.Default,
): WordSearchGrid = generateWordSearch(WORD_SEARCH_BANK.shuffled(random).take(count), size, random)

/**
 * The straight run of squares from [from] to [to], or null if they don't line up.
 *
 * Straight means one of the eight directions — same row, same column, or a true diagonal where the
 * row and column distances match. A knight's-move drag is not a word, and neither is a single tap.
 */
fun wordSearchPath(from: Cell, to: Cell): List<Cell>? {
    val dRow = to.row - from.row
    val dCol = to.col - from.col
    if (dRow == 0 && dCol == 0) return null
    val steps = maxOf(kotlin.math.abs(dRow), kotlin.math.abs(dCol))
    if (dRow != 0 && dCol != 0 && kotlin.math.abs(dRow) != kotlin.math.abs(dCol)) return null
    val stepRow = dRow / steps
    val stepCol = dCol / steps
    return (0..steps).map { Cell(from.row + stepRow * it, from.col + stepCol * it) }
}

/**
 * The word the player just selected, if that selection is one of the hidden words.
 *
 * Matched in both directions: a word running right-to-left in the grid is just as findable
 * left-to-right, and asking the player to notice which way round it was hidden would be a puzzle
 * about pedantry rather than about looking.
 */
fun wordSearchMatch(grid: WordSearchGrid, from: Cell, to: Cell): WordPlacement? {
    val path = wordSearchPath(from, to) ?: return null
    if (path.any { it.row !in 0 until grid.size || it.col !in 0 until grid.size }) return null
    return grid.placements.firstOrNull { it.cells == path || it.cells == path.reversed() }
}

/**
 * Minutes earned for finding [found] of [total] words.
 *
 * Partial credit on purpose: giving up two words short of a full grid should still be worth
 * something, or the honest move for a stuck player is to stare at it rather than stop.
 */
fun wordSearchReward(found: Int, total: Int): Int {
    if (total <= 0 || found <= 0) return 1
    val capped = found.coerceAtMost(total)
    return maxOf(1, (capped * WORD_SEARCH_FULL_REWARD + total / 2) / total)
}

/** What a completely solved grid pays. */
const val WORD_SEARCH_FULL_REWARD = 8

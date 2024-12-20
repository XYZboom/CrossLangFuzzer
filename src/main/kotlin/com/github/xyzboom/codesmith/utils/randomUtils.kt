package com.github.xyzboom.codesmith.utils

import kotlin.random.Random

@Suppress("DuplicatedCode")
fun <T> rouletteSelection(list: List<T>, weights: List<Int>, random: Random = Random.Default): T {
    require(list.size == weights.size) {
        "require list size same as weights size, but got: ${list.size} and ${weights.size}"
    }
    val sum = weights.sum()
    val choose = random.nextInt(sum)
    var current = 0
    for ((index, weight) in weights.withIndex()) {
        current += weight
        if (choose < current) {
            return list[index]
        }
    }
    throw IllegalStateException("should not be here!")
}

@Suppress("DuplicatedCode")
fun <T> rouletteSelection(list: Array<T>, weights: List<Int>, random: Random = Random.Default): T {
    require(list.size == weights.size) {
        "require list size same as weights size, but got: ${list.size} and ${weights.size}"
    }
    val sum = weights.sum()
    val choose = random.nextInt(sum)
    var current = 0
    for ((index, weight) in weights.withIndex()) {
        current += weight
        if (choose < current) {
            return list[index]
        }
    }
    throw IllegalStateException("should not be here!")
}

fun <T> choice(vararg collections: Collection<T>, random: Random = Random.Default): T {
    val choose = rouletteSelection(collections, collections.map { it.size }, random)
    return choose.random(random)
}


@Suppress("NOTHING_TO_INLINE")
inline fun Random.nextBoolean(probability: Float): Boolean {
    return nextFloat() < probability
}
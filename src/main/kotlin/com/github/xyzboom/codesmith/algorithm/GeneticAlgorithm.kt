package com.github.xyzboom.codesmith.algorithm

import com.github.xyzboom.codesmith.utils.nextBoolean
import com.github.xyzboom.codesmith.utils.rouletteSelection
import kotlin.random.Random

abstract class GeneticAlgorithm<T>(
    val offspringRound: Int,
    val crossoverProbability: Float,
    val mutateProbability: Float,
    val random: Random = Random
) {
    abstract fun init(): List<T>
    abstract fun getOffspring(parentA: T, parentB: T): Pair<T, T>
    abstract fun mutate(element: T): T
    abstract fun evaluate(element: T): Int
    fun nextRound(preList: List<T>): List<T> {
        val crossoverResult = ArrayList<T>(preList.size)
        val chooseAsParent = ArrayList<T>((preList.size * crossoverProbability).toInt())
        for (t in preList) {
            if (random.nextBoolean(crossoverProbability)) {
                chooseAsParent.add(t)
            } else {
                crossoverResult.add(t)
            }
        }
        val parentIt = chooseAsParent.iterator()
        while (parentIt.hasNext()) {
            val parentA = parentIt.next()
            if (!parentIt.hasNext()) {
                crossoverResult.add(parentA)
                break
            }
            val parentB = parentIt.next()
            val (offSpringA, offSpringB) = getOffspring(parentA, parentB)
            crossoverResult.add(offSpringA)
            crossoverResult.add(offSpringB)
        }
        val result = ArrayList<T>(preList.size)
        for (t in crossoverResult) {
            if (random.nextBoolean(mutateProbability)) {
                result.add(mutate(t))
            } else {
                result.add(t)
            }
        }
        return result
    }

    fun run(listener: (List<T>, List<Int>) -> Boolean) {
        val list = init()
        var nextInput = ArrayList(list)
        repeat(offspringRound) {
            val next = nextRound(nextInput)
            val eval = next.map { evaluate(it) }
            if (!listener(next, eval)) {
                return
            }
            nextInput = ArrayList(list.size)
            repeat(list.size) {
                val selection = rouletteSelection(next, eval, random)
                nextInput.add(selection)
            }
        }
    }
}
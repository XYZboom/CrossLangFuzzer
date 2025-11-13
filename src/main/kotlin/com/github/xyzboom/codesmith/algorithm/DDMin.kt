package com.github.xyzboom.codesmith.algorithm

import kotlin.math.max
import kotlin.math.min

class DDMin<T>(private val testFunc: (List<T>) -> Boolean) {

    fun execute(input: List<T>): List<T> {
        if (input.size <= 1) {
            return input
        }

        return executeRecursive(input, 2)
    }

    private fun executeRecursive(
        input: List<T>,
        n: Int
    ): List<T> {
        if (input.isEmpty()) return input
        if (input.size == 1) {
            if (testFunc(emptyList())) return emptyList()
            return input
        }
        // split input into n parts
        val parts = partition(input, n)

        // test on complement of each part
        for (i in parts.indices) {
            val complement = getComplement(parts, i)
            // if complement passes test, run on complement
            if (testFunc(complement)) {
                return executeRecursive(complement, max(n - 1, 2))
            }
        }

        // if we can split further, do it. Otherwise, return the result.
        return if (n < input.size) {
            executeRecursive(input, min(n * 2, input.size))
        } else {
            input
        }
    }

    /**
     * split list into n parts
     */
    private fun <T> partition(list: List<T>, n: Int): List<List<T>> {
        val result = mutableListOf<List<T>>()
        val chunkSize = list.size / n
        var remainder = list.size % n

        var start = 0
        for (i in 0 until n) {
            val currentChunkSize = chunkSize + if (remainder > 0) 1 else 0
            remainder--

            if (start < list.size) {
                val end = minOf(start + currentChunkSize, list.size)
                result.add(list.subList(start, end))
                start = end
            }
        }

        return result
    }

    private fun <T> getComplement(parts: List<List<T>>, excludeIndex: Int): List<T> {
        val result = mutableListOf<T>()
        for (i in parts.indices) {
            if (i != excludeIndex) {
                result.addAll(parts[i])
            }
        }
        return result
    }
}

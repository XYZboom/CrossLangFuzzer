package com.github.xyzboom.codesmith.tree.generator.utils

fun <Arg1, Bound, R> ((Arg1, Bound) -> R).bind(bound: Bound): ((Arg1) -> R) =
    { t1 -> this.invoke(t1, bound) }

fun <Arg1, Arg2, Bound, R> ((Arg1, Arg2, Bound) -> R).bind(bound: Bound): ((Arg1, Arg2) -> R) =
    { t1, t2 -> this.invoke(t1, t2, bound) }

fun <Arg1, Bound1, Bound2, R> ((Arg1, Bound1, Bound2) -> R).bind(bound1: Bound1, bound2: Bound2): ((Arg1) -> R) =
    { t1 -> this.invoke(t1, bound1, bound2) }
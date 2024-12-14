package com.github.xyzboom.codesmith.generator

val lowerLetters = 'a'..'z'
/**
 * Avoid to generate names like getXXX or setXXX.
 */
val lowerStartingLetters = listOf('a'..'z') - 'g' - 's'
val upperLetters = 'A'..'Z'
val numbers = '0'..'9'
val letters = lowerLetters + upperLetters
val lettersAndNumbers = letters + numbers

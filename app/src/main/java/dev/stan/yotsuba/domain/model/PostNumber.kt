package dev.stan.yotsuba.domain.model

/**
 * How many of a post number's last digits repeat: 1 for most numbers, 2 for dubs (..11),
 * 3 for trips (..222), and so on. The "get" the board culture tints.
 */
fun repeatingTail(no: Long): Int {
    val digits = no.toString()
    return digits.reversed().takeWhile { it == digits.last() }.length
}

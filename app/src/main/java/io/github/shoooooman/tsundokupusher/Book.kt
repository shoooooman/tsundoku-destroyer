package io.github.shoooooman.tsundokupusher

import java.util.*

class Book(val name: String, val pages: Int, val deadline: Date, var readPages: Int) {
    override fun toString(): String {
        return name
    }
}


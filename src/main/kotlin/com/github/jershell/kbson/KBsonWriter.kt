package com.github.jershell.kbson

import org.bson.BsonDocument
import org.bson.BsonDocumentWriter

class KBsonWriter(document: BsonDocument) : BsonDocumentWriter(document) {

    fun skip() {
        this.state = when (this.state) {
            State.NAME -> State.VALUE
            State.VALUE -> State.NAME
            else -> throw IllegalStateException("Undefined state")
        }

    }

}
package com.github.jershell.kbson

import org.bson.AbstractBsonWriter
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredMemberFunctions

val GET_STATE_FUNCTION: KFunction<*> = AbstractBsonWriter::class.declaredMemberFunctions.first{it.name == "getState"}
val SET_STATE_FUNCTION: KFunction<*> = AbstractBsonWriter::class.declaredMemberFunctions.first{it.name  == "setState"}

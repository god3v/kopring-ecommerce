package com.loopers.support.error

interface ErrorType {
    val status: ErrorStatus
    val code: String
    val message: String
}

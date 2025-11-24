package org.android.starter.network

interface IApiV1 {
    suspend fun hello(msg: String): String
}
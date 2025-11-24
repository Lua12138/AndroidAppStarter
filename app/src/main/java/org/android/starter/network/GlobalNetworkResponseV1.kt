package org.android.starter.network

sealed class GlobalNetworkResponseV1<out T> {
    data class Success<T>(val response: T) : GlobalNetworkResponseV1<T>()
    data class ServerFailed<T>(val responseCode: Int, val responseMsg: String) :
        GlobalNetworkResponseV1<T>()

    data class LocalFailed<T>(val message: String, val error: Throwable) :
        GlobalNetworkResponseV1<T>()
}
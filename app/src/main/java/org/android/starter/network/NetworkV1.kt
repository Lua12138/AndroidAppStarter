package org.android.starter.network

import android.content.Context
import android.util.Log
import com.google.net.cronet.okhttptransport.CronetCallFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.android.starter.BuildConfig
import org.chromium.net.CronetEngine
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max


object NetworkV1 {
    private val TAG = "NetworkV1"

    private lateinit var mCronetEngine: CronetEngine
    private lateinit var mOkhttp: OkHttpClient
    private lateinit var mRetrofit: Retrofit
    private lateinit var mExecutors: ExecutorService
    private val mAPI: IApiV1 by lazy { this.mRetrofit.create(IApiV1::class.java) }

    val httpRttMS: Int
        get() = this.mCronetEngine.httpRttMs
    val tcpRttMS: Int
        get() = this.mCronetEngine.transportRttMs

    private suspend fun <T> safeApiCall(
        name: String,
        call: suspend () -> T
    ): GlobalNetworkResponseV1<T> {
        // make sure the response is on the IO thread
        val response = withContext(Dispatchers.IO) {
            try {
                GlobalNetworkResponseV1.Success(call())
            } catch (e: HttpException) {
                GlobalNetworkResponseV1.ServerFailed(
                    e.code(),
                    e.response()?.errorBody()?.string() ?: ""
                )
            } catch (e: SocketTimeoutException) {
                GlobalNetworkResponseV1.LocalFailed("Network Timeout, please try again later.", e)
            } catch (e: java.io.IOException) {
                GlobalNetworkResponseV1.LocalFailed("IO Error", e)
            } catch (e: Throwable) {
                GlobalNetworkResponseV1.LocalFailed("unknown exception error", e)
            }
        }

        Log.d(TAG, "call api $name, response: $response")

        return response
    }

    fun init(context: Context) {
        val cacheDir = File(context.cacheDir, "cronet")
        cacheDir.mkdirs()

        val parallel = max(Runtime.getRuntime().availableProcessors() / 2, 4)
        this.mExecutors = Executors.newWorkStealingPool(parallel)

        this.mCronetEngine = CronetEngine.Builder(context)
            .enableBrotli(true)
            .enableQuic(true)
            .enableHttp2(true)
            .enableHttpCache(
                CronetEngine.Builder.HTTP_CACHE_DISK,
                1024 * 1024 * 128
            )
            .setStoragePath(cacheDir.absolutePath)
            .build()

        val factory = CronetCallFactory.newBuilder(this.mCronetEngine)
            .setCallTimeoutMillis(15 * 1000)
            .setCallbackExecutorService(this.mExecutors)
            .build()

        this.mOkhttp = OkHttpClient.Builder()
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply {
                if (BuildConfig.DEBUG) {
                    val interceptor = HttpLoggingInterceptor()
                    interceptor.level = HttpLoggingInterceptor.Level.HEADERS
                    addInterceptor(interceptor)
                }
            }
            .build()

        this.mRetrofit = Retrofit.Builder()
            .client(this.mOkhttp)
            // TODO replace your base url
            .baseUrl("http://localhost")
            .addConverterFactory(GsonConverterFactory.create())
            .callFactory(factory)
            .build()
    }

    fun openConnection(url: URL): URLConnection {
        return this.mCronetEngine.openConnection(url)
    }

    suspend fun hello(msg: String): GlobalNetworkResponseV1<String> {
        return this.safeApiCall("hello") {
            this.mAPI.hello(msg)
        }
    }
}
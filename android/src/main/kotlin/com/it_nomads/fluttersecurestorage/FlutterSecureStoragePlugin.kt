package com.it_nomads.fluttersecurestorage

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.PrintWriter
import java.io.StringWriter

class FlutterSecureStoragePlugin : MethodCallHandler, FlutterPlugin {
    private var channel: MethodChannel? = null
    private var secureStorage: FlutterSecureStorage? = null
    private var secureStorageOptions: Map<String, Any?>? = null
    private var workerThread: HandlerThread? = null
    private var workerThreadHandler: Handler? = null
    private var binding: FlutterPlugin.FlutterPluginBinding? = null
    private var authenticatedOptions: Map<String, Any?>? = null
    private var authenticatedAtElapsedRealtime = 0L
    private var pendingAuthenticationOptions: Map<String, Any?>? = null
    private val pendingAuthenticationCalls = mutableListOf<PendingMethodCall>()

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        this.binding = binding
        workerThread = HandlerThread(WORKER_THREAD_NAME).also { it.start() }
        workerThreadHandler = Handler(requireNotNull(workerThread).looper)
        channel = MethodChannel(binding.binaryMessenger, CHANNEL_NAME).also {
            it.setMethodCallHandler(this)
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        workerThread?.quitSafely()
        workerThread = null
        workerThreadHandler = null

        channel?.setMethodCallHandler(null)
        channel = null
        secureStorage = null
        secureStorageOptions = null
        authenticatedOptions = null
        authenticatedAtElapsedRealtime = 0L
        pendingAuthenticationOptions = null
        pendingAuthenticationCalls.clear()
        this.binding = null
    }

    override fun onMethodCall(call: MethodCall, rawResult: Result) {
        val result = MethodResultWrapper(rawResult)
        val handler = workerThreadHandler
        if (handler == null) {
            result.error("Unavailable", "Worker thread is not available", null)
            return
        }

        handler.post { handleMethodCallSafely(call, result) }
    }

    private fun handleMethodCallSafely(call: MethodCall, result: Result) {
        try {
            val arguments = call.arguments.asStringAnyMap().takeIf {
                call.arguments != null
            }
            if (arguments == null) {
                result.error("InvalidArgument", "No arguments passed to method call", null)
                return
            }

            val options = arguments["options"].asStringAnyMap()
            val config = AndroidStorageConfig.from(options)
            if (
                config.enforceBiometrics &&
                call.method !in AUTHENTICATION_QUERY_METHODS &&
                !isAuthenticationFresh(options, config)
            ) {
                secureStorage = null
                secureStorageOptions = null
                enqueueAuthentication(call, result, options, config)
                return
            }

            handleMethodCall(call, result, arguments, options, config)
        } catch (throwable: Throwable) {
            // Some OEM builds throw Error subclasses (for example NoSuchFieldError)
            // from Android Keystore framework code. Do not let them escape this
            // HandlerThread and crash the app process.
            val stackTrace = StringWriter().also {
                throwable.printStackTrace(PrintWriter(it))
            }.toString()
            result.error("Exception", "Error while executing method: ${call.method}", stackTrace)
        }
    }

    private fun handleMethodCall(
        call: MethodCall,
        result: Result,
        arguments: Map<String, Any?>,
        options: Map<String, Any?>,
        config: AndroidStorageConfig,
    ) {
        if (call.method == "isBiometricAvailable") {
            result.success(authenticator.isAvailable(config))
            return
        }
        if (call.method == "isDeviceSecure") {
            result.success(authenticator.isDeviceSecure())
            return
        }
        if (!initSecureStorage(result, options)) return

        when (call.method) {
            "write" -> handleWrite(arguments, result)
            "read" -> result.success(storage.read(arguments["key"] as? String))
            "readAll" -> result.success(storage.readAll())
            "containsKey" -> result.success(storage.containsKey(arguments["key"] as? String))
            "delete" -> {
                storage.delete(arguments["key"] as? String)
                result.success(null)
            }
            "deleteAll" -> {
                storage.deleteAll()
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    private fun enqueueAuthentication(
        call: MethodCall,
        result: Result,
        options: Map<String, Any?>,
        config: AndroidStorageConfig,
    ) {
        val pendingOptions = pendingAuthenticationOptions
        if (pendingOptions != null) {
            if (pendingOptions == options) {
                pendingAuthenticationCalls += PendingMethodCall(call, result)
            } else {
                result.error(
                    "AuthenticationInProgress",
                    "Authentication is already in progress for another storage namespace.",
                    null,
                )
            }
            return
        }

        pendingAuthenticationOptions = options
        pendingAuthenticationCalls += PendingMethodCall(call, result)
        authenticator.authenticate(
            config = config,
            onSuccess = {
                val handler = workerThreadHandler
                if (handler == null) {
                    drainPendingAuthenticationCalls().forEach {
                        it.result.error(
                            "Unavailable",
                            "Worker thread is not available after authentication",
                            null,
                        )
                    }
                } else {
                    handler.post {
                        authenticatedOptions = options
                        authenticatedAtElapsedRealtime = SystemClock.elapsedRealtime()
                        val pendingCalls = drainPendingAuthenticationCalls()
                        pendingCalls.forEach {
                            handleMethodCallSafely(it.call, it.result)
                        }
                    }
                }
            },
            onError = { exception ->
                val handler = workerThreadHandler
                if (handler == null) {
                    drainPendingAuthenticationCalls().forEach {
                        it.result.error(
                            "AuthenticationFailed",
                            exception.message,
                            exception.toString(),
                        )
                    }
                } else {
                    handler.post {
                        drainPendingAuthenticationCalls().forEach {
                            it.result.error(
                                "AuthenticationFailed",
                                exception.message,
                                exception.toString(),
                            )
                        }
                    }
                }
            },
        )
    }

    private fun isAuthenticationFresh(
        options: Map<String, Any?>,
        config: AndroidStorageConfig,
    ): Boolean {
        if (authenticatedOptions != options) return false
        val validityMillis =
            config.userAuthenticationValidityDurationSeconds * 1000L
        return SystemClock.elapsedRealtime() - authenticatedAtElapsedRealtime < validityMillis
    }

    private fun drainPendingAuthenticationCalls(): List<PendingMethodCall> {
        val calls = pendingAuthenticationCalls.toList()
        pendingAuthenticationCalls.clear()
        pendingAuthenticationOptions = null
        return calls
    }

    private fun initSecureStorage(result: Result, options: Map<String, Any?>): Boolean {
        if (secureStorage != null && secureStorageOptions == options) return true

        val applicationContext = binding?.applicationContext
        if (applicationContext == null) {
            result.error("Unavailable", "Plugin is not attached to an engine", null)
            return false
        }

        return try {
            secureStorage = FlutterSecureStorage(applicationContext, options)
            secureStorageOptions = options
            true
        } catch (exception: Exception) {
            result.error(
                "RESET_FAILED",
                "Failed to reset and initialize encrypted preferences",
                exception.toString(),
            )
            false
        }
    }

    private fun handleWrite(arguments: Map<String, Any?>, result: Result) {
        val value = arguments["value"] as? String
        if (value == null) {
            result.error("InvalidArgument", "Value is null", null)
            return
        }

        storage.write(arguments["key"] as? String, value)
        result.success(null)
    }

    private val storage: FlutterSecureStorage
        get() = checkNotNull(secureStorage) { "Secure storage has not been initialized." }

    private val authenticator: BiometricAuthenticator
        get() = BiometricAuthenticator(
            checkNotNull(binding?.applicationContext) {
                "Plugin is not attached to an engine."
            },
        )

    private fun Any?.asStringAnyMap(): Map<String, Any?> {
        val map = this as? Map<*, *> ?: return emptyMap()
        return map.entries
            .filter { it.key is String }
            .associate { it.key as String to it.value }
    }

    private class MethodResultWrapper(private val methodResult: Result) : Result {
        private val handler = Handler(Looper.getMainLooper())

        override fun success(result: Any?) {
            handler.post { methodResult.success(result) }
        }

        override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
            handler.post { methodResult.error(errorCode, errorMessage, errorDetails) }
        }

        override fun notImplemented() {
            handler.post { methodResult.notImplemented() }
        }
    }

    private data class PendingMethodCall(
        val call: MethodCall,
        val result: Result,
    )

    private companion object {
        const val CHANNEL_NAME = "plugins.it_nomads.com/flutter_secure_storage"
        const val WORKER_THREAD_NAME = "fluttersecurestorage.worker"
        val AUTHENTICATION_QUERY_METHODS = setOf(
            "isBiometricAvailable",
            "isDeviceSecure",
        )
    }
}

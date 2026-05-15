package com.it_nomads.fluttersecurestorage

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
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
    private var workerThread: HandlerThread? = null
    private var workerThreadHandler: Handler? = null
    private var binding: FlutterPlugin.FlutterPluginBinding? = null

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
            handleMethodCall(call, result)
        } catch (exception: Exception) {
            val stackTrace = StringWriter().also {
                exception.printStackTrace(PrintWriter(it))
            }.toString()
            result.error("Exception", "Error while executing method: ${call.method}", stackTrace)
        }
    }

    private fun handleMethodCall(call: MethodCall, result: Result) {
        val arguments = call.arguments.asStringAnyMap().takeIf { call.arguments != null }
        if (arguments == null) {
            result.error("InvalidArgument", "No arguments passed to method call", null)
            return
        }

        val options = arguments["options"].asStringAnyMap()
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

    private fun initSecureStorage(result: Result, options: Map<String, Any?>): Boolean {
        if (secureStorage != null) return true

        val applicationContext = binding?.applicationContext
        if (applicationContext == null) {
            result.error("Unavailable", "Plugin is not attached to an engine", null)
            return false
        }

        return try {
            secureStorage = FlutterSecureStorage(applicationContext, options)
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

    private companion object {
        const val CHANNEL_NAME = "plugins.it_nomads.com/flutter_secure_storage"
        const val WORKER_THREAD_NAME = "fluttersecurestorage.worker"
    }
}

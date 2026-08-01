package com.example.crash

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class CrashHandler private constructor(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Log.e("CrashHandler", "Uncaught exception detected", throwable)

            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stackTrace = sw.toString()

            val deviceInfo = buildString {
                append("Device: ").append(Build.MANUFACTURER.replaceFirstChar { it.uppercase() })
                    .append(" ").append(Build.MODEL)
                    .append(" (").append(Build.DEVICE).append(")\n")
                append("Android Version: ").append(Build.VERSION.RELEASE)
                    .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
                append("App Version: ").append(BuildConfig.VERSION_NAME)
                    .append(" (Code ").append(BuildConfig.VERSION_CODE).append(")\n")
                append("Thread: ").append(thread.name).append("\n")
                append("Exception: ").append(throwable.javaClass.name).append(": ")
                    .append(throwable.localizedMessage ?: "No message")
            }

            val intent = Intent(context, CrashActivity::class.java).apply {
                putExtra(CrashActivity.EXTRA_CRASH_INFO, deviceInfo)
                putExtra(CrashActivity.EXTRA_STACK_TRACE, stackTrace)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("CrashHandler", "Error handling crash", e)
            defaultHandler?.uncaughtException(thread, throwable)
            return
        }

        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(10)
    }

    companion object {
        fun init(context: Context) {
            val handler = CrashHandler(context.applicationContext)
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }
}

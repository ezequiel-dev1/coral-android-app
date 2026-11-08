package com.coral.printagent.printer

import android.util.Log
import com.coral.printagent.model.PrinterResult
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PrinterClient(
    private val printerIp: String,
    private val printerPort: Int = 9100,
) {
    /** Opens and closes a TCP connection without sending data. */
    suspend fun testConnection(): PrinterResult =
        execute("Connected to $printerIp:$printerPort", null)

    suspend fun testPrint(): PrinterResult =
        execute("Test receipt sent to printer", EscPosCommands.testReceipt())

    suspend fun openCashDrawer(pin: Int = 0): PrinterResult =
        execute("Drawer pulse sent to printer", EscPosCommands.drawerPulse(pin))

    suspend fun printRaw(data: ByteArray): PrinterResult =
        execute("Print job sent to printer", data)

    private suspend fun execute(successMessage: String, data: ByteArray?): PrinterResult =
        withContext(Dispatchers.IO) {
            var writeTimedOut = false
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(printerIp, printerPort), CONNECT_TIMEOUT_MS)
                    socket.soTimeout = WRITE_TIMEOUT_MS
                    if (data != null) {
                        // Blocking writes ignore soTimeout, so a watchdog closes the socket.
                        coroutineScope {
                            val watchdog = launch {
                                delay(WRITE_TIMEOUT_MS.toLong())
                                writeTimedOut = true
                                socket.close()
                            }
                            try {
                                socket.getOutputStream().apply {
                                    write(data)
                                    flush()
                                }
                            } finally {
                                watchdog.cancel()
                            }
                        }
                    }
                }
                Log.d(TAG, successMessage)
                PrinterResult.Success(successMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Printer operation failed", e)
                PrinterResult.Failure(describe(e, writeTimedOut))
            }
        }

    private fun describe(e: Exception, writeTimedOut: Boolean): String = when {
        writeTimedOut -> "Write timed out after ${WRITE_TIMEOUT_MS / 1000}s"
        e is SocketTimeoutException ->
            "Connection timed out after ${CONNECT_TIMEOUT_MS / 1000}s. Check the IP address and that the printer is on."
        e is ConnectException ->
            "Connection refused by $printerIp:$printerPort. Check the port number."
        e is NoRouteToHostException ->
            "No route to $printerIp. Is the tablet on the same Wi-Fi network?"
        e is UnknownHostException -> "Unknown host: $printerIp"
        e is IOException -> "Socket error: ${e.message ?: e.javaClass.simpleName}"
        else -> "Unexpected error: ${e.message ?: e.javaClass.simpleName}"
    }

    private companion object {
        const val TAG = "PrinterClient"
        const val CONNECT_TIMEOUT_MS = 3000
        const val WRITE_TIMEOUT_MS = 5000
    }
}

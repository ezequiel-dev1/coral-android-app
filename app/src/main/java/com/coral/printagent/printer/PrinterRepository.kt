package com.coral.printagent.printer

import com.coral.printagent.model.PrinterResult
import com.coral.printagent.model.PrinterSettings

class PrinterRepository {
    private fun client(s: PrinterSettings) = PrinterClient(s.ip, s.port)

    suspend fun testConnection(s: PrinterSettings): PrinterResult = client(s).testConnection()

    suspend fun testPrint(s: PrinterSettings): PrinterResult = client(s).testPrint()

    suspend fun openCashDrawer(s: PrinterSettings): PrinterResult =
        client(s).openCashDrawer(s.drawerPin)
}

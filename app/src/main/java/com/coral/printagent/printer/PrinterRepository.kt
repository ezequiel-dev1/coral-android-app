package com.coral.printagent.printer

import com.coral.printagent.model.PrintJob
import com.coral.printagent.model.PrinterResult
import com.coral.printagent.model.PrinterSettings
import com.coral.printagent.model.PrinterTarget
import com.coral.printagent.model.endpoint

class PrinterRepository {
    private fun client(s: PrinterSettings, target: PrinterTarget): PrinterClient {
        val e = s.endpoint(target)
        return PrinterClient(e.ip, e.port)
    }

    suspend fun testConnection(s: PrinterSettings, target: PrinterTarget): PrinterResult =
        client(s, target).testConnection()

    suspend fun testPrint(s: PrinterSettings, target: PrinterTarget): PrinterResult =
        client(s, target).testPrint(target.label)

    suspend fun print(s: PrinterSettings, job: PrintJob): PrinterResult =
        client(s, job.printer).printRaw(EscPosCommands.fromJob(job, s.drawerPin))

    /** The cash drawer is only connected to the register printer. */
    suspend fun openCashDrawer(s: PrinterSettings): PrinterResult =
        client(s, PrinterTarget.REGISTER).openCashDrawer(s.drawerPin)
}

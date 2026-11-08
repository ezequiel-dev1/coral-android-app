package com.coral.printagent.model

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class Align { LEFT, CENTER, RIGHT }

data class PrintLine(
    val text: String,
    val align: Align = Align.LEFT,
    val bold: Boolean = false,
    val underline: Boolean = false,
    /** Character magnification 1..4, applied to width and height. */
    val size: Int = 1,
)

data class PrintJob(
    val lines: List<PrintLine>,
    val feed: Int = 3,
    val cut: Boolean = true,
    val openDrawer: Boolean = false,
    val printer: PrinterTarget = PrinterTarget.KITCHEN,
) {
    companion object {
        const val MAX_LINES = 500
        const val MAX_LINE_LENGTH = 200
        const val MAX_SIZE = 4
        const val MAX_FEED = 10

        /**
         * Parses the JSON body of POST /print.
         *
         * ```
         * {
         *   "lines": [
         *     "plain string line",
         *     {"text": "TOTAL", "align": "center", "bold": true, "underline": false, "size": 2}
         *   ],
         *   "feed": 3,          // optional, blank lines before cut (0..10)
         *   "cut": true,        // optional, default true
         *   "openDrawer": false, // optional, default false; register printer only
         *   "printer": "kitchen" // optional: "kitchen" or "register". Default "kitchen",
         *                        // or "register" when openDrawer is true
         * }
         * ```
         *
         * @throws IllegalArgumentException when the payload is invalid (maps to HTTP 400).
         */
        fun parse(body: String): PrintJob {
            val root = try {
                JSONObject(body)
            } catch (e: JSONException) {
                throw IllegalArgumentException("Body must be a JSON object")
            }
            val arr: JSONArray = root.optJSONArray("lines")
                ?: throw IllegalArgumentException("\"lines\" array is required")
            require(arr.length() in 1..MAX_LINES) { "\"lines\" must contain 1..$MAX_LINES entries" }

            val lines = (0 until arr.length()).map { i ->
                when (val item = arr.get(i)) {
                    is String -> PrintLine(checkText(item))
                    is JSONObject -> PrintLine(
                        text = checkText(item.optString("text", "")),
                        align = parseAlign(item.optString("align", "left")),
                        bold = item.optBoolean("bold", false),
                        underline = item.optBoolean("underline", false),
                        size = item.optInt("size", 1).also {
                            require(it in 1..MAX_SIZE) { "\"size\" must be 1..$MAX_SIZE" }
                        },
                    )
                    else -> throw IllegalArgumentException("lines[$i] must be a string or object")
                }
            }
            val feed = root.optInt("feed", 3)
            require(feed in 0..MAX_FEED) { "\"feed\" must be 0..$MAX_FEED" }
            val openDrawer = root.optBoolean("openDrawer", false)
            val printer = if (root.has("printer")) {
                PrinterTarget.fromJson(root.optString("printer"))
            } else if (openDrawer) {
                PrinterTarget.REGISTER
            } else {
                PrinterTarget.KITCHEN
            }
            require(!openDrawer || printer == PrinterTarget.REGISTER) {
                "Only the register printer is connected to the cash drawer"
            }
            return PrintJob(
                lines = lines,
                feed = feed,
                cut = root.optBoolean("cut", true),
                openDrawer = openDrawer,
                printer = printer,
            )
        }

        private fun checkText(t: String): String {
            require(t.length <= MAX_LINE_LENGTH) { "A line exceeds $MAX_LINE_LENGTH characters" }
            return t
        }

        private fun parseAlign(v: String): Align = when (v.lowercase()) {
            "left" -> Align.LEFT
            "center" -> Align.CENTER
            "right" -> Align.RIGHT
            else -> throw IllegalArgumentException("\"align\" must be left, center or right")
        }
    }
}

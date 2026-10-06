package com.unodevelopments.cblsshmngr.sql

fun toCsv(columns: List<String>, rows: List<List<String?>>): String {
    fun cell(value: String?): String {
        val text = value.orEmpty()
        val escaped = text.replace("\"", "\"\"")
        return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"$escaped\"" else escaped
    }
    return buildString {
        append(columns.joinToString(",") { cell(it) })
        rows.forEach { row ->
            append('\n')
            append(row.joinToString(",") { cell(it) })
        }
    }
}

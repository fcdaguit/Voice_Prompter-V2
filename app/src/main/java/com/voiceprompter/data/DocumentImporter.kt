package com.voiceprompter.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads a user-picked .txt or .docx into an editable script. */
class DocumentImporter(private val context: Context) {

    sealed interface Result {
        data class Success(val title: String, val body: String) : Result
        data class Failure(val reason: String) : Result
    }

    suspend fun import(uri: Uri): Result = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "Imported script"
        val baseTitle = displayName.substringBeforeLast('.', displayName).trim()
            .ifBlank { "Imported script" }

        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.Failure("Could not open that file")

            val body = stream.use { input ->
                when {
                    displayName.endsWith(".docx", ignoreCase = true) ->
                        DocxExtractor.extractText(input)

                    displayName.endsWith(".txt", ignoreCase = true) ->
                        input.readBytes().toString(Charsets.UTF_8)

                    else -> return@withContext Result.Failure(
                        "Only .txt and .docx files can be imported"
                    )
                }
            }

            if (body.isBlank()) {
                Result.Failure("That file has no readable text")
            } else {
                Result.Success(baseTitle, normalise(body))
            }
        } catch (e: DocxExtractor.NotADocxException) {
            Result.Failure(e.message ?: "That is not a readable Word document")
        } catch (e: Exception) {
            Result.Failure("Could not read that file: ${e.message ?: e::class.simpleName}")
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    /** Normalise line endings and strip the BOM so the editor and splitter behave. */
    private fun normalise(text: String): String =
        text.removePrefix("﻿")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
}

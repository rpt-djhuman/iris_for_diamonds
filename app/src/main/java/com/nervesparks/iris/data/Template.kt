// Template.kt
package com.nervesparks.iris.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File

data class Template(
    val name: String,
    val version: String,
    val description: String,
    val input: List<TemplateField>,
    val output: List<TemplateField>,
    val prompt: String
) {
    companion object {
        fun fromJson(jsonString: String): Template? {
            return try {
                Gson().fromJson(jsonString, Template::class.java)
            } catch (e: Exception) {
                null
            }
        }

        fun fromFile(file: File): Template? {
            return try {
                val jsonString = file.readText()
                fromJson(jsonString)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun generatePrompt(inputValues: Map<String, String>): String {
        var result = prompt
        inputValues.forEach { (key, value) ->
            result = result.replace("{{$key}}", value)
        }
        return result
    }

    fun toInputJson(inputValues: Map<String, String>): String {
        return Gson().toJson(inputValues)
    }

    fun toJson(): String {
        return Gson().toJson(this)
    }
}

data class TemplateField(
    val name: String,
    val description: String,
    val type: String,
    val min: Int? = null,
    val max: Int? = null,
    val options: List<String>? = null,
    @SerializedName("default_value") val defaultValue: String? = null  // Match JSON property name
)


//data class SavedTemplate(
//    val id: String,  // Unique identifier
//    val name: String,
//    val description: String,
//    val jsonContent: String,  // The raw JSON content of the template
//    val savedDate: Long = System.currentTimeMillis()
//)
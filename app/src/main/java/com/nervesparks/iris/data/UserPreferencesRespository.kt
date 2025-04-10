package com.nervesparks.iris.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

private const val USER_PREFERENCES_NAME = "user_preferences"
private const val KEY_DEFAULT_MODEL_NAME = "default_model_name"
private const val KEY_SAVED_TEMPLATES = "saved_templates"

class UserPreferencesRepository private constructor(context: Context) {

    private val sharedPreferences =
        context.applicationContext.getSharedPreferences(USER_PREFERENCES_NAME, Context.MODE_PRIVATE)

    // Get the default model name, returns empty string if not set
    fun getDefaultModelName(): String {
        return sharedPreferences.getString(KEY_DEFAULT_MODEL_NAME, "") ?: ""
    }

    // Set the default model name
    fun setDefaultModelName(modelName: String) {
        sharedPreferences.edit().putString(KEY_DEFAULT_MODEL_NAME, modelName).apply()
    }

    fun getSavedTemplates(): List<SavedTemplate> {
        val templatesJson = sharedPreferences.getString(KEY_SAVED_TEMPLATES, "[]") ?: "[]"
        return try {
            Gson().fromJson(templatesJson, object : TypeToken<List<SavedTemplate>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveTemplate(template: Template, jsonContent: String): SavedTemplate {
        val savedTemplate = SavedTemplate(
            id = UUID.randomUUID().toString(),
            name = template.name,
            description = template.description,
            jsonContent = jsonContent
        )

        val currentTemplates = getSavedTemplates().toMutableList()
        currentTemplates.add(savedTemplate)

        val templatesJson = Gson().toJson(currentTemplates)
        sharedPreferences.edit().putString(KEY_SAVED_TEMPLATES, templatesJson).apply()

        return savedTemplate
    }

    fun deleteSavedTemplate(templateId: String): Boolean {
        val currentTemplates = getSavedTemplates().toMutableList()
        val initialSize = currentTemplates.size

        currentTemplates.removeAll { it.id == templateId }

        if (currentTemplates.size != initialSize) {
            val templatesJson = Gson().toJson(currentTemplates)
            sharedPreferences.edit().putString(KEY_SAVED_TEMPLATES, templatesJson).apply()
            return true
        }

        return false
    }

    companion object {
        @Volatile
        private var INSTANCE: UserPreferencesRepository? = null

        fun getInstance(context: Context): UserPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserPreferencesRepository(context).also { INSTANCE = it }
            }
        }
    }
}
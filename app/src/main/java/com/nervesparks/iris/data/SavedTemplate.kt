package com.nervesparks.iris.data

data class SavedTemplate(
    val id: String,  // Unique identifier
    val name: String,
    val description: String,
    val jsonContent: String,  // The raw JSON content of the template
    val savedDate: Long = System.currentTimeMillis()
)
// TemplateChatScreen.kt
package com.nervesparks.iris.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nervesparks.iris.MainViewModel
import com.nervesparks.iris.data.Template
import com.nervesparks.iris.data.TemplateField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateChatScreen(
    template: Template,
    viewModel: MainViewModel,
    onBackPressed: () -> Unit
) {
    var inputValues by remember { mutableStateOf(mutableMapOf<String, String>()) }
    var validationErrors by remember { mutableStateOf(mutableMapOf<String, String>()) }
    var isSubmitting by remember { mutableStateOf(false) }
    var modelResponse by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var showPrompt by remember { mutableStateOf(false) }
    var generatedPrompt by remember { mutableStateOf<String?>(null) }
    var useDefaultValues by remember { mutableStateOf(mutableMapOf<String, Boolean>()) }

    val context = LocalContext.current

    // Initialize input values
    LaunchedEffect(template) {
        val initialValues = mutableMapOf<String, String>()
        val initialDefaultFlags = mutableMapOf<String, Boolean>()

        template.input.forEach { field ->
            // Initialize with default values for fields that have them
            initialValues[field.name] = when (field.type) {
                "int" -> field.min?.toString() ?: "0"
                "categorical" -> field.options?.firstOrNull() ?: ""
                else -> ""
            }

            // Initialize default value flags (false by default)
            if (field.defaultValue != null) {
                initialDefaultFlags[field.name] = false
            }
        }

        inputValues = initialValues
        useDefaultValues = initialDefaultFlags
    }

    fun validateInputs(): Boolean {
        val errors = mutableMapOf<String, String>()

        template.input.forEach { field ->
            val value = inputValues[field.name] ?: ""

            when (field.type) {
                "string" -> {
                    if (field.min != null && value.length < field.min) {
                        errors[field.name] = "Minimum length is ${field.min} characters"
                    }
                    if (field.max != null && value.length > field.max) {
                        errors[field.name] = "Maximum length is ${field.max} characters"
                    }
                }
                "int" -> {
                    val intValue = value.toIntOrNull()
                    if (intValue == null) {
                        errors[field.name] = "Must be a valid number"
                    } else {
                        if (field.min != null && intValue < field.min) {
                            errors[field.name] = "Minimum value is ${field.min}"
                        }
                        if (field.max != null && intValue > field.max) {
                            errors[field.name] = "Maximum value is ${field.max}"
                        }
                    }
                }
                "categorical" -> {
                    if (value.isEmpty() || (field.options != null && !field.options.contains(value))) {
                        errors[field.name] = "Please select a valid option"
                    }
                }
            }
        }

        validationErrors = errors
        return errors.isEmpty()
    }

    fun generatePrompt(): String {
        // Create a JSON object from the input values
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("{")

        // Add each field as a key-value pair
        inputValues.entries.forEachIndexed { index, (key, value) ->
            // Add quotes around the key
            jsonBuilder.append("\"$key\": ")

            // Add quotes around string values
            val field = template.input.find { it.name == key }
            when (field?.type) {
                "int" -> jsonBuilder.append(value) // No quotes for numbers
                else -> jsonBuilder.append("\"$value\"") // Quotes for strings and categorical
            }

            // Add comma if not the last item
            if (index < inputValues.size - 1) {
                jsonBuilder.append(", ")
            }
        }

        jsonBuilder.append("}")
        return jsonBuilder.toString()
    }

    fun submitForm() {
        if (validateInputs()) {
            isSubmitting = true
            isGenerating = true
            modelResponse = null

            val prompt = generatePrompt()
            generatedPrompt = prompt // Store the generated prompt

            // Use the viewModel's scope
            viewModel.generateTemplateResponse(prompt) { response ->
                modelResponse = response
                isGenerating = false
                isSubmitting = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(template.name) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ) ,
                actions = {
                    IconButton(
                        onClick = {
                            // Save the current template
                            val jsonContent = template.toJson() // Add this method to Template class
                            viewModel.saveCurrentTemplate(jsonContent)
                            Toast.makeText(context, "Template saved", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save template",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .background(Color(0xFF0F172A)),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            items(template.input.size) { index ->
                val field = template.input[index]
                val value = inputValues[field.name] ?: ""
                val error = validationErrors[field.name]

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A2234))
                        .padding(12.dp)
                ) {
                    Text(
                        text = field.description,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Add checkbox for fields with default values
                    if (field.defaultValue != null && field.type == "string") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = useDefaultValues[field.name] ?: false,
                                onCheckedChange = { isChecked ->
                                    // Update the default value flag
                                    useDefaultValues = useDefaultValues.toMutableMap().apply {
                                        this[field.name] = isChecked
                                    }

                                    // If checked, populate with default value, otherwise clear
                                    if (isChecked) {
                                        inputValues = inputValues.toMutableMap().apply {
                                            this[field.name] = field.defaultValue ?: ""
                                        }
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF2563EB),
                                    uncheckedColor = Color.Gray,
                                    checkmarkColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "Use default value",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    when (field.type) {
                        "categorical" -> {
                            // Dropdown for categorical fields
                            var expanded by remember { mutableStateOf(false) }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = value,
                                    onValueChange = { /* Read-only for dropdown */ },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF2A3346),
                                        unfocusedContainerColor = Color(0xFF2A3346),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        cursorColor = Color.White,
                                        focusedBorderColor = Color(0xFF2563EB),
                                        unfocusedBorderColor = Color.Gray,
                                        errorBorderColor = Color.Red
                                    ),
                                    readOnly = true,
                                    trailingIcon = {
                                        IconButton(onClick = { expanded = !expanded }) {
                                            Icon(
                                                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Dropdown",
                                                tint = Color.White
                                            )
                                        }
                                    },
                                    isError = error != null
                                )

                                // Invisible clickable overlay to open dropdown
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable { expanded = !expanded }
                                )

                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier
                                        .background(Color(0xFF1E293B))
                                        .width(with(LocalDensity.current) {
                                            // Match width of the TextField
                                            (LocalConfiguration.current.screenWidthDp - 32).dp
                                        })
                                ) {
                                    field.options?.forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = option,
                                                    color = Color.White
                                                )
                                            },
                                            onClick = {
                                                inputValues = inputValues.toMutableMap().apply {
                                                    this[field.name] = option
                                                }
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        "int" -> {
                            // Number input for int fields
                            OutlinedTextField(
                                value = value,
                                onValueChange = { newValue ->
                                    // Only allow digits
                                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                        inputValues = inputValues.toMutableMap().apply {
                                            this[field.name] = newValue
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF2A3346),
                                    unfocusedContainerColor = Color(0xFF2A3346),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color.White,
                                    focusedBorderColor = Color(0xFF2563EB),
                                    unfocusedBorderColor = Color.Gray,
                                    errorBorderColor = Color.Red
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number
                                ),
                                isError = error != null
                            )
                        }
                        else -> {
                            // Default text input for string fields
                            OutlinedTextField(
                                value = value,
                                onValueChange = { newValue ->
                                    if (field.max == null || newValue.length <= field.max) {
                                        inputValues = inputValues.toMutableMap().apply {
                                            this[field.name] = newValue
                                        }

                                        // If user manually changes the value, uncheck the "use default" checkbox
                                        if (field.defaultValue != null && useDefaultValues[field.name] == true) {
                                            useDefaultValues = useDefaultValues.toMutableMap().apply {
                                                this[field.name] = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF2A3346),
                                    unfocusedContainerColor = Color(0xFF2A3346),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color.White,
                                    focusedBorderColor = Color(0xFF2563EB),
                                    unfocusedBorderColor = Color.Gray,
                                    errorBorderColor = Color.Red
                                ),
                                isError = error != null,
                                singleLine = field.max != null && field.max < 100,
                                maxLines = if (field.max != null && field.max > 100) 5 else 1,
                                readOnly = useDefaultValues[field.name] == true // Make field read-only if using default value
                            )
                        }
                    }

                    if (error != null) {
                        Text(
                            text = error,
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }

                    // Show character count for string fields with max length
                    if (field.type == "string" && field.max != null) {
                        Text(
                            text = "${value.length}/${field.max}",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = { submitForm() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB)
                    ),
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Generate",
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            if (generatedPrompt != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Show Generated Prompt",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )

                        Switch(
                            checked = showPrompt,
                            onCheckedChange = { showPrompt = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF2563EB),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.Gray
                            )
                        )
                    }

                    if (showPrompt) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1A2234))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Generated Prompt",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Text(
                                text = generatedPrompt ?: "",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F172A))
                                    .padding(12.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(
                                    onClick = {
                                        // Copy to clipboard
                                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clipData = ClipData.newPlainText("Generated Prompt", generatedPrompt)
                                        clipboardManager.setPrimaryClip(clipData)
                                        // Show toast or some feedback
                                        Toast.makeText(context, "Prompt copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy to clipboard",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Display model response
            if (modelResponse != null || isGenerating) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A2234))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Response",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (isGenerating && modelResponse == null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Generating response...",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            Text(
                                text = modelResponse ?: "",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            if (isGenerating) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    color = Color(0xFF2563EB)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
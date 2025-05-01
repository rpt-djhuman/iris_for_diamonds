package com.nervesparks.iris

import android.content.Context
import android.llama.cpp.LLamaAndroid
import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.nervesparks.iris.data.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import java.io.File
import java.util.Locale
import java.util.UUID
import com.nervesparks.iris.data.Template
import com.nervesparks.iris.data.SavedTemplate
import com.nervesparks.iris.utils.ResourceMetrics
import com.nervesparks.iris.utils.ResourceMonitor
import kotlinx.coroutines.flow.Flow


class MainViewModel(private val llamaAndroid: LLamaAndroid = LLamaAndroid.instance(), private val userPreferencesRepository: UserPreferencesRepository): ViewModel() {
    companion object {
//        @JvmStatic
//        private val NanosPerSecond = 1_000_000_000.0
    }


    private val _defaultModelName = mutableStateOf("")
    val defaultModelName: State<String> = _defaultModelName

    private val _currentTemplate = mutableStateOf<Template?>(null)
    val currentTemplate: State<Template?> = _currentTemplate

    private val _savedTemplates = mutableStateOf<List<SavedTemplate>>(emptyList())
    val savedTemplates: State<List<SavedTemplate>> = _savedTemplates

    private var isTemplateModelWarmedUp = false

    public lateinit var resourceMonitor: ResourceMonitor
    var resourceMetricsList = mutableListOf<ResourceMetrics>()
    var averageResourceMetrics by mutableStateOf(ResourceMetrics())

    var modelLoadTime by mutableStateOf(0L)
    var modelLoadMemoryImpact by mutableStateOf(0f)
    var isBenchmarkInProgress by mutableStateOf(false)
    var benchmarkStage by mutableStateOf("Not started")

    var baselineMemoryUsage by mutableStateOf(0f)  // Memory usage before model loading
    var peakMemoryUsage by mutableStateOf(0f)      // Peak memory during inference
    var modelMemoryImpact by mutableStateOf(0f)    // Calculated model memory impact

    var loadedModelPath by mutableStateOf("")

    // Initialize the resource monitor in a function
    fun initResourceMonitor(context: Context) {
        resourceMonitor = ResourceMonitor(context)
    }
    fun prepareForBenchmark(context: Context) {
        // Initialize resource monitor if not already done
        if (!::resourceMonitor.isInitialized) {
            resourceMonitor = ResourceMonitor(context)
        }

        // Force garbage collection to get more accurate baseline
        System.gc()
        Thread.sleep(500)  // Give GC time to complete

        // Measure baseline memory before model loading
        val baselineMetrics = resourceMonitor.collectMetrics()
        baselineMemoryUsage = baselineMetrics.memoryUsageMB

        Log.d("Benchmark", "Baseline memory usage: $baselineMemoryUsage MB")
    }


    fun setCurrentTemplate(template: Template?) {
        _currentTemplate.value = template
    }

    fun loadBuiltInTemplates(context: Context): List<Template> {
        val templates = mutableListOf<Template>()
        try {
            context.assets.list("templates")?.forEach { filename ->
                if (filename.endsWith(".json")) {
                    val jsonString = context.assets.open("templates/$filename").bufferedReader().use { it.readText() }
                    Template.fromJson(jsonString)?.let { templates.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error loading built-in templates: ${e.message}")
        }
        return templates
    }

    fun collectResourceMetrics(): ResourceMetrics {
        if (!::resourceMonitor.isInitialized) {
            throw IllegalStateException("Resource monitor not initialized")
        }
        return resourceMonitor.collectMetrics()
    }

    fun generateTemplateResponse(prompt: String, onResponse: (String) -> Unit) {
        viewModelScope.launch {
            val responseBuilder = StringBuilder()

            try {
                // Send the raw JSON prompt directly without chat formatting
                llamaAndroid.sendRawPrompt(prompt)
                    .catch { e ->
                        Log.e(tag, "generateTemplateResponse() failed", e)
                        onResponse("Error: ${e.message}")
                    }
                    .collect { response ->
                        responseBuilder.append(response)
                        onResponse(responseBuilder.toString())
                    }
            } catch (e: Exception) {
                Log.e(tag, "generateTemplateResponse() exception", e)
                onResponse("Error: ${e.message}")
            }
        }
    }

    fun sendMessage(prompt: String) {
        // Clear the current message field
        message = ""

        // Add to messages console
        if (prompt.isNotEmpty()) {
            if (first) {
                addMessage("system", "This is a conversation between User and Iris, a friendly chatbot. Iris is helpful, kind, honest, good at writing, and never fails to answer any requests immediately and with precision.")
                first = false
            }

            addMessage("user", prompt)

            viewModelScope.launch {
                try {
                    llamaAndroid.send(llamaAndroid.getTemplate(messages))
                        .catch {
                            Log.e(tag, "send() failed", it)
                            addMessage("error", it.message ?: "")
                        }
                        .collect { response ->
                            // Create a new assistant message with the response
                            if (getIsMarked()) {
                                addMessage("codeBlock", response)
                            } else {
                                addMessage("assistant", response)
                            }
                        }
                } finally {
                    if (!getIsCompleteEOT()) {
                        trimEOT()
                    }
                }
            }
        }
    }

    init {
        loadDefaultModelName()
    }
    private fun loadDefaultModelName(){
        _defaultModelName.value = userPreferencesRepository.getDefaultModelName()
    }

    fun setDefaultModelName(modelName: String){
        userPreferencesRepository.setDefaultModelName(modelName)
        _defaultModelName.value = modelName
    }

    lateinit var selectedModel: String
    private val tag: String? = this::class.simpleName

    var messages by mutableStateOf(

        listOf<Map<String, String>>(),
    )
        private set
    var newShowModal by mutableStateOf(false)
    var showDownloadInfoModal by mutableStateOf(false)
    var user_thread by mutableStateOf(0f)
    var topP by mutableStateOf(0.1f)
    var topK by mutableStateOf(20)
    var temp by mutableStateOf(0.3f)

    var allModels by mutableStateOf(
        listOf(
            mapOf(
                "name" to "Llama-3.2-1B 8-bit (Largest)",
                "source" to "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q8_0.gguf?download=true",
                "destination" to "Llama-3.2-1B-Instruct-Q8_0.gguf"
            ),
            mapOf(
                "name" to "Qwen2.5-0.5B 8-bit (Medium)",
                "source" to "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q8_0.gguf?download=true",
                "destination" to "qwen2.5-0.5b-instruct-q8_0.gguf"
            ),
            mapOf(
                "name" to "SmolLM2-135M 8-bit (Smallest)",
                "source" to "https://huggingface.co/unsloth/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q8_0.gguf?download=true",
                "destination" to "SmolLM2-135M-Instruct-Q8_0.gguf"
            ),

            )
    )

    private var first by mutableStateOf(
        true
    )
    var userSpecifiedThreads by mutableIntStateOf(2)
    var message by mutableStateOf("")
        private set

    var userGivenModel by mutableStateOf("")
    var SearchedName by mutableStateOf("")

    private var textToSpeech:TextToSpeech? = null

    var textForTextToSpeech = ""
    var stateForTextToSpeech by mutableStateOf(true)
        private set

    var eot_str = ""


    var refresh by mutableStateOf(false)

    fun loadExistingModels(directory: File) {
        // List models in the directory that end with .gguf
        var anyModelExists = false
        directory.listFiles { file -> file.extension == "gguf" }?.forEach { file ->
            val modelName = file.name
            Log.i("This is the modelname", modelName)
            if (!allModels.any { it["name"] == modelName }) {
                allModels += mapOf(
                    "name" to modelName,
                    "source" to "local",
                    "destination" to file.name
                )
                anyModelExists = true
            }
        }

        if (defaultModelName.value.isNotEmpty()) {
            val loadedDefaultModel = allModels.find { model -> model["name"] == defaultModelName.value }

            if (loadedDefaultModel != null) {
                val destinationPath = File(directory, loadedDefaultModel["destination"].toString())
                if(loadedModelName.value == "") {
                    load(destinationPath.path, userThreads = user_thread.toInt())
                }
                anyModelExists = true
                currentDownloadable = Downloadable(
                    loadedDefaultModel["name"].toString(),
                    Uri.parse(loadedDefaultModel["source"].toString()),
                    destinationPath
                )
            } else {
                // Handle case where the model is not found
                allModels.find { model ->
                    val destinationPath = File(directory, model["destination"].toString())
                    destinationPath.exists()
                }?.let { model ->
                    val destinationPath = File(directory, model["destination"].toString())
                    if(loadedModelName.value == "") {
                        load(destinationPath.path, userThreads = user_thread.toInt())
                    }
                    anyModelExists = true
                    currentDownloadable = Downloadable(
                        model["name"].toString(),
                        Uri.parse(model["source"].toString()),
                        destinationPath
                    )
                }
                showModal = !anyModelExists
            }
        } else{
            showModal = !anyModelExists
            allModels.find { model ->
                val destinationPath = File(directory, model["destination"].toString())
                destinationPath.exists()
            }?.let { model ->
                val destinationPath = File(directory, model["destination"].toString())
                if(loadedModelName.value == "") {
                    load(destinationPath.path, userThreads = user_thread.toInt())
                }
                currentDownloadable = Downloadable(
                    model["name"].toString(),
                    Uri.parse(model["source"].toString()),
                    destinationPath
                )
            }
            // Attempt to find and load the first model that exists in the combined logic

        }
    }



    fun textToSpeech(context: Context) {
        if (!getIsSending()) {
            // If TTS is already initialized, stop it first
            textToSpeech?.stop()

            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.let { txtToSpeech ->
                        txtToSpeech.language = Locale.US
                        txtToSpeech.setSpeechRate(1.0f)

                        // Add a unique utterance ID for tracking
                        val utteranceId = UUID.randomUUID().toString()

                        txtToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onDone(utteranceId: String?) {
                                // Reset state when speech is complete
                                CoroutineScope(Dispatchers.Main).launch {
                                    stateForTextToSpeech = true
                                }
                            }

                            override fun onError(utteranceId: String?) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    stateForTextToSpeech = true
                                }
                            }

                            override fun onStart(utteranceId: String?) {
                                // Update state to indicate speech is playing
                                CoroutineScope(Dispatchers.Main).launch {
                                    stateForTextToSpeech = false
                                }
                            }
                        })

                        txtToSpeech.speak(
                            textForTextToSpeech,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            utteranceId
                        )
                    }
                }
            }
        }
    }



    fun stopTextToSpeech() {
        textToSpeech?.apply {
            stop()  // Stops current speech
            shutdown()  // Releases the resources
        }
        textToSpeech = null

        // Reset state to allow restarting
        stateForTextToSpeech = true
    }



    var toggler by mutableStateOf(false)
    var showModal by  mutableStateOf(false)
    var showAlert by mutableStateOf(false)
    var switchModal by mutableStateOf(false)
    var currentDownloadable: Downloadable? by mutableStateOf(null)

    override fun onCleared() {
        textToSpeech?.shutdown()
        super.onCleared()

        viewModelScope.launch {
            try {

                llamaAndroid.unload()

            } catch (exc: IllegalStateException) {
                addMessage("error", exc.message ?: "")
            }
        }
    }

    fun send() {
        val userMessage = removeExtraWhiteSpaces(message)
        message = ""

        // Add to messages console.
        if (userMessage != "" && userMessage != " ") {
            if(first){
                addMessage("system", "This is a conversation between User and Iris, a friendly chatbot. Iris is helpful, kind, honest, good at writing, and never fails to answer any requests immediately and with precision.")
                addMessage("user", "Hi")
                addMessage("assistant", "How may I help You?")
                first = false
            }

            addMessage("user", userMessage)


            viewModelScope.launch {
                try {
                    val prompt = llamaAndroid.getTemplate(messages)
                    Log.d("Messages", messages.toString())
                    Log.d("Prompt", prompt)
                    llamaAndroid.send(llamaAndroid.getTemplate(messages))
                        .catch {
                            Log.e(tag, "send() failed", it)
                            addMessage("error", it.message ?: "")
                        }
                        .collect { response ->
                            // Create a new assistant message with the response
                            if (getIsMarked()) {
                                addMessage("codeBlock", response)

                            } else {
                                addMessage("assistant", response)
                            }
                        }
                }
                finally {
                    if (!getIsCompleteEOT()) {
                        trimEOT()
                    }
                }



            }
        }



    }

//    fun bench(pp: Int, tg: Int, pl: Int, nr: Int = 1) {
//        viewModelScope.launch {
//            try {
//                val start = System.nanoTime()
//                val warmupResult = llamaAndroid.bench(pp, tg, pl, nr)
//                val end = System.nanoTime()
//
//                messages += warmupResult
//
//                val warmup = (end - start).toDouble() / NanosPerSecond
//                messages += "Warm up time: $warmup seconds, please wait..."
//
//                if (warmup > 5.0) {
//                    messages += "Warm up took too long, aborting benchmark"
//                    return@launch
//                }
//
//                messages += llamaAndroid.bench(512, 128, 1, 3)
//            } catch (exc: IllegalStateException) {
//                Log.e(tag, "bench() failed", exc)
//                messages += exc.message!!
//            }
//        }
//    }

    suspend fun unload(){
        llamaAndroid.unload()
    }

    var tokensList = mutableListOf<String>() // Store emitted tokens
    var benchmarkStartTime: Long = 0L // Track the benchmark start time
    var tokensPerSecondsFinal: Double by mutableStateOf(0.0) // Track tokens per second and trigger UI updates
    var isBenchmarkingComplete by mutableStateOf(false) // Flag to track if benchmarking is complete

    fun myCustomBenchmark() {
        viewModelScope.launch {
            try {
                tokensList.clear()
                resourceMetricsList.clear()
                benchmarkStartTime = System.currentTimeMillis()
                isBenchmarkingComplete = false

                // Launch a coroutine to update metrics every second
                launch {
                    while (!isBenchmarkingComplete) {
                        delay(1000L)
                        val elapsedTime = System.currentTimeMillis() - benchmarkStartTime
                        if (elapsedTime > 0) {
                            tokensPerSecondsFinal = tokensList.size.toDouble() / (elapsedTime / 1000.0)

                            // Collect resource metrics
                            val metrics = resourceMonitor.collectMetrics()
                            resourceMetricsList.add(metrics)

                            // Calculate average metrics
                            if (resourceMetricsList.isNotEmpty()) {
                                averageResourceMetrics = ResourceMetrics(
                                    cpuUsage = resourceMetricsList.map { it.cpuUsage }.average().toFloat(),
                                    memoryUsageMB = resourceMetricsList.map { it.memoryUsageMB }.average().toFloat(),
                                    totalMemoryMB = resourceMetricsList.last().totalMemoryMB,
                                    batteryLevel = resourceMetricsList.last().batteryLevel,
                                    batteryTemperature = resourceMetricsList.map { it.batteryTemperature }.average().toFloat(),
                                    batteryCurrentDrawMa = resourceMetricsList.map { it.batteryCurrentDrawMa }.average().toInt()
                                )
                            }
                        }
                    }
                }

                llamaAndroid.myCustomBenchmark()
                    .collect { emittedString ->
                        if (emittedString != null) {
                            tokensList.add(emittedString)
                            Log.d(tag, "Token collected: $emittedString")
                        }
                    }
            } catch (exc: Exception) {
                Log.e(tag, "myCustomBenchmark() error", exc)
            } finally {
                val elapsedTime = System.currentTimeMillis() - benchmarkStartTime
                val finalTokensPerSecond = if (elapsedTime > 0) {
                    tokensList.size.toDouble() / (elapsedTime / 1000.0)
                } else {
                    0.0
                }

                tokensPerSecondsFinal = finalTokensPerSecond
                isBenchmarkingComplete = true

                // Collect final metrics
                if (resourceMetricsList.isNotEmpty()) {
                    averageResourceMetrics = ResourceMetrics(
                        cpuUsage = resourceMetricsList.map { it.cpuUsage }.average().toFloat(),
                        memoryUsageMB = resourceMetricsList.map { it.memoryUsageMB }.average().toFloat(),
                        totalMemoryMB = resourceMetricsList.last().totalMemoryMB,
                        batteryLevel = resourceMetricsList.last().batteryLevel,
                        batteryTemperature = resourceMetricsList.map { it.batteryTemperature }.average().toFloat(),
                        batteryCurrentDrawMa = resourceMetricsList.map { it.batteryCurrentDrawMa }.average().toInt()
                    )
                }
            }
        }
    }





    var loadedModelName = mutableStateOf("");

    fun load(pathToModel: String, userThreads: Int) {
        viewModelScope.launch {
            try {
                llamaAndroid.unload()
            } catch (exc: IllegalStateException) {
                Log.e(tag, "load() failed", exc)
            }
            try {
                loadedModelPath = pathToModel  // Store the path
                var modelName = pathToModel.split("/")
                loadedModelName.value = modelName.last()
                newShowModal = false
                showModal = false
                showAlert = true
                llamaAndroid.load(pathToModel, userThreads = userThreads, topK = topK, topP = topP, temp = temp)
                showAlert = false
            } catch (exc: IllegalStateException) {
                Log.e(tag, "load() failed", exc)
            }
            showModal = false
            showAlert = false
            eot_str = llamaAndroid.send_eot_str()
        }
    }
    private fun addMessage(role: String, content: String) {
        val newMessage = mapOf("role" to role, "content" to content)

        messages = if (messages.isNotEmpty() && messages.last()["role"] == role) {
            val lastMessageContent = messages.last()["content"] ?: ""
            val updatedContent = "$lastMessageContent$content"
            val updatedLastMessage = messages.last() + ("content" to updatedContent)
            messages.toMutableList().apply {
                set(messages.lastIndex, updatedLastMessage)
            }
        } else {
            messages + listOf(newMessage)
        }
    }

    private fun trimEOT() {
        if (messages.isEmpty()) return
        val lastMessageContent = messages.last()["content"] ?: ""
        // Only slice if the content is longer than the EOT string
        if (lastMessageContent.length < eot_str.length) return

        val updatedContent = lastMessageContent.slice(0..(lastMessageContent.length-eot_str.length))
        val updatedLastMessage = messages.last() + ("content" to updatedContent)
        messages = messages.toMutableList().apply {
            set(messages.lastIndex, updatedLastMessage)
        }
        messages.last()["content"]?.let { Log.e(tag, it) }
    }

    private fun removeExtraWhiteSpaces(input: String): String {
        // Replace multiple white spaces with a single space
        return input.replace("\\s+".toRegex(), " ")
    }

    private fun parseTemplateJson(chatData: List<Map<String, String>> ):String{
        var chatStr = ""
        for (data in chatData){
            val role = data["role"]
            val content = data["content"]
            if (role != "log"){
                chatStr += "$role \n$content \n"
            }

        }
        return chatStr
    }
    fun updateMessage(newMessage: String) {
        message = newMessage
    }

    fun clear() {
        messages = listOf(

        )
        first = true
    }

    fun log(message: String) {
//        addMessage("log", message)
    }

    fun getIsSending(): Boolean {
        return llamaAndroid.getIsSending()
    }

    private fun getIsMarked(): Boolean {
        return llamaAndroid.getIsMarked()
    }

    fun getIsCompleteEOT(): Boolean{
        return llamaAndroid.getIsCompleteEOT()
    }

    fun stop() {
        llamaAndroid.stopTextGeneration()
    }

    // In MainViewModel.kt
    fun startComprehensiveBenchmark(context: Context, modelPath: String? = null) {
        viewModelScope.launch {
            // Reset all metrics first
            resetBenchmarkMetrics()

            isBenchmarkInProgress = true
            benchmarkStage = "Preparing"
            resourceMetricsList.clear() // Clear previous metrics

            // Initialize resource monitor if not already done
            if (!::resourceMonitor.isInitialized) {
                resourceMonitor = ResourceMonitor(context)
            }

            try {

                // 2. Unload any currently loaded model
                benchmarkStage = "Unloading current model"
                try {
                    llamaAndroid.unload()
                    loadedModelName.value = ""
                    delay(1000) // Give time for unloading to complete
                } catch (e: Exception) {
                    Log.e("Benchmark", "Error unloading model: ${e.message}")
                }

                // 1. Force garbage collection for more accurate baseline
                System.gc()
                delay(2000)  // Give GC time to complete

                // 3. Measure model load time and memory impact
                benchmarkStage = "Measuring baseline metrics"
                val baselineMetrics = resourceMonitor.collectMetrics()
                baselineMemoryUsage = baselineMetrics.memoryUsageMB
                val baselineBatteryLevel = baselineMetrics.batteryLevel
                val baselineBatteryTemp = baselineMetrics.batteryTemperature

                Log.d("Benchmark", "Baseline memory: $baselineMemoryUsage MB")


                benchmarkStage = "Loading model"
                val loadStartTime = System.currentTimeMillis()

                // Start collecting metrics during loading
                val metricsJob = launch {
                    while (benchmarkStage == "Loading model") {
                        val metrics = resourceMonitor.collectMetrics()
                        resourceMetricsList.add(metrics)
                        delay(200) // Sample every 200ms during loading
                    }
                }

                // Load the model
                if (modelPath != null) {
                    // Load the model with standard parameters
                    llamaAndroid.load(modelPath, userThreads = user_thread.toInt(), topK = topK, topP = topP, temp = temp)

                    // Update model info
                    loadedModelPath = modelPath
                    val modelName = modelPath.split("/").last()
                    loadedModelName.value = modelName

                    // Calculate load time
                    val loadEndTime = System.currentTimeMillis()
                    modelLoadTime = loadEndTime - loadStartTime

                    // Add small delay to ensure data has time to stabilise
                    delay(3000)

                    // Measure memory after loading
                    val postLoadMetrics = resourceMonitor.collectMetrics()
                    modelLoadMemoryImpact = postLoadMetrics.memoryUsageMB - baselineMemoryUsage

                    // Add debug logging
                    Log.d("Benchmark", "Baseline memory: $baselineMemoryUsage MB")
                    Log.d("Benchmark", "Post-load memory: ${postLoadMetrics.memoryUsageMB} MB")
                    Log.d("Benchmark", "Memory impact: $modelLoadMemoryImpact MB")

                    // Calculate peak memory during loading
                    peakMemoryUsage = resourceMetricsList
                        .maxOfOrNull { it.memoryUsageMB } ?: postLoadMetrics.memoryUsageMB

                    // Stop collecting loading metrics
                    metricsJob.cancel()

                    Log.d("Benchmark", "Model load time: $modelLoadTime ms")
                    Log.d("Benchmark", "Model memory impact: $modelLoadMemoryImpact MB")

                    // 4. Now proceed with the inference benchmark
                    benchmarkStage = "Running inference benchmark"
                    resourceMetricsList.clear() // Clear for inference metrics
                    tokensList.clear()
                    benchmarkStartTime = System.currentTimeMillis()
                    isBenchmarkingComplete = false

                    // Launch a coroutine to update metrics every second during inference
                    launch {
                        while (!isBenchmarkingComplete) {
                            delay(1000L)
                            val metrics = resourceMonitor.collectMetrics()
                            resourceMetricsList.add(metrics)

                            // Update peak memory if current usage is higher
                            if (metrics.memoryUsageMB > peakMemoryUsage) {
                                peakMemoryUsage = metrics.memoryUsageMB
                            }

                            // Calculate tokens per second
                            val elapsedTime = System.currentTimeMillis() - benchmarkStartTime
                            if (elapsedTime > 0) {
                                tokensPerSecondsFinal = tokensList.size.toDouble() / (elapsedTime / 1000.0)
                            }

                            // Update average metrics
                            updateAverageResourceMetrics()
                        }
                    }

                    // Run the actual benchmark
                    llamaAndroid.myCustomBenchmark()
                        .collect { emittedString ->
                            if (emittedString != null) {
                                tokensList.add(emittedString)
                            }
                        }

                    benchmarkStage = "Completed"
                } else {
                    benchmarkStage = "Error: Model path is null"
                    Log.e("Benchmark", "Model path is null")
                }
            } catch (e: Exception) {
                benchmarkStage = "Error: ${e.message}"
                Log.e("Benchmark", "Benchmark error: ${e.message}")
            } finally {
                isBenchmarkingComplete = true
                isBenchmarkInProgress = false

                // Final metrics update
                updateAverageResourceMetrics()
            }
        }
    }

    // Helper function to update average metrics
    private fun updateAverageResourceMetrics() {
        if (resourceMetricsList.isNotEmpty()) {
            averageResourceMetrics = ResourceMetrics(
                cpuUsage = resourceMetricsList.map { it.cpuUsage }.average().toFloat(),
                memoryUsageMB = resourceMetricsList.map { it.memoryUsageMB }.average().toFloat(),
                totalMemoryMB = resourceMetricsList.last().totalMemoryMB,
                batteryLevel = resourceMetricsList.last().batteryLevel,
                batteryTemperature = resourceMetricsList.map { it.batteryTemperature }.average().toFloat(),
                batteryCurrentDrawMa = resourceMetricsList.map { it.batteryCurrentDrawMa }.average().toInt()
            )
        }
    }

    fun resetBenchmarkMetrics() {
        // Reset all benchmark-related metrics
        modelLoadTime = 0L
        modelLoadMemoryImpact = 0f
        baselineMemoryUsage = 0f
        peakMemoryUsage = 0f
        modelMemoryImpact = 0f
        tokensPerSecondsFinal = 0.0

        // Clear collections
        resourceMetricsList.clear()
        tokensList.clear()

        // Reset average metrics
        averageResourceMetrics = ResourceMetrics()

        // Reset benchmark state
        isBenchmarkingComplete = false
        benchmarkStage = "Not started"
    }

    fun loadSavedTemplates() {
        _savedTemplates.value = userPreferencesRepository.getSavedTemplates()
    }

    fun saveCurrentTemplate(jsonContent: String): Boolean {
        val template = currentTemplate.value ?: return false
        val savedTemplate = userPreferencesRepository.saveTemplate(template, jsonContent)
        loadSavedTemplates() // Refresh the list
        return true
    }

    fun deleteSavedTemplate(templateId: String): Boolean {
        val result = userPreferencesRepository.deleteSavedTemplate(templateId)
        if (result) {
            loadSavedTemplates() // Refresh the list
        }
        return result
    }

    fun loadSavedTemplate(savedTemplate: SavedTemplate) {
        val template = Template.fromJson(savedTemplate.jsonContent)
        if (template != null) {
            setCurrentTemplate(template)
        }
    }



}

fun sentThreadsValue(){

}
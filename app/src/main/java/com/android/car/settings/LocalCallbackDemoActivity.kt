package com.android.car.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.android.car.settings.ui.theme.CarSettingTheme
import com.b231001.bmaterial.runtime.localcallback.LocalCallback
import com.b231001.bmaterial.runtime.localcallback.LocalCallbackConfig
import com.b231001.bmaterial.runtime.localcallback.LocalCallbackEmitter
import com.b231001.bmaterial.runtime.localcallback.LocalCallbackKey
import com.b231001.bmaterial.runtime.localcallback.LocalCallbackRegistration
import com.b231001.bmaterial.runtime.localcallback.LocalCallbackRegistry
import com.b231001.bmaterial.runtime.localcallback.LocalCallbackSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class LocalCallbackDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CarSettingTheme {
                LocalCallbackDemoScreen()
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, LocalCallbackDemoActivity::class.java)
    }
}

@Composable
fun LocalCallbackDemoLauncher(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(modifier.padding(16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("LocalCallback",)
            Text(
                "One external registration, many local consumers / " +
                    "Một đăng ký nguồn, nhiều nơi sử dụng.",
            )
            Button(
                onClick = {
                    context.startActivity(LocalCallbackDemoActivity.intent(context))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open demo / Mở bản demo")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun LocalCallbackDemoScreen() {
    val context = LocalContext.current
    val controller = remember(context) { LocalCallbackDemoController(context) }
    DisposableEffect(controller) {
        onDispose(controller::close)
    }

    val lifecycle by controller.callback.lifecycle.collectAsState()
    val sourceStats by controller.sourceStats.collectAsState()
    val consumerValues by controller.consumerValues.collectAsState()
    val consumerDeliveries by controller.consumerDeliveries.collectAsState()
    val stressResult by controller.stressResult.collectAsState()
    val systemBenchmarkRunning by controller.systemBenchmarkRunning.collectAsState()
    val systemBenchmarkStatus by controller.systemBenchmarkStatus.collectAsState()
    val systemBenchmarkResults by controller.systemBenchmarkResults.collectAsState()
    val revision by controller.revision.collectAsState()
    val snapshot = remember(revision, lifecycle) { controller.callback.snapshot() }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .semantics { testTagsAsResourceId = true }
                .testTag(LocalCallbackDemoTags.ROOT)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("LocalCallback performance demo")
            Text(
                "The expensive source is registered once and locally fans out data. " +
                    "Nguồn tốn chi phí chỉ đăng ký một lần rồi phân phối dữ liệu nội bộ.",
            )

            DemoMetricsCard(
                status = lifecycle.status.name,
                activeSubscribers = lifecycle.subscriberCount,
                sourceStats = sourceStats,
                sourceEvents = snapshot.sourceEvents,
                deliveries = snapshot.enqueuedDeliveries,
                drops = snapshot.droppedSourceEvents + snapshot.droppedDeliveries
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = controller::startConsumers,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(LocalCallbackDemoTags.START)
                ) {
                    Text("Start 3 / Bắt đầu")
                }
                OutlinedButton(
                    onClick = controller::stopOneConsumer,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(LocalCallbackDemoTags.STOP_ONE)
                ) {
                    Text("Stop B / Dừng B")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = controller::emitOne,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(LocalCallbackDemoTags.EMIT)
                ) {
                    Text("Emit / Phát")
                }
                OutlinedButton(
                    onClick = controller::emitBurst,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(LocalCallbackDemoTags.BURST)
                ) {
                    Text("Burst 1,000")
                }
                OutlinedButton(
                    onClick = controller::stopConsumers,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(LocalCallbackDemoTags.STOP_ALL)
                ) {
                    Text("Stop all")
                }
            }

            ConsumerCard(
                values = consumerValues,
                deliveries = consumerDeliveries
            )

            Button(
                onClick = controller::runStress,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(LocalCallbackDemoTags.STRESS)
            ) {
                Text("Run 1,024,000-delivery stress test / Chạy stress test")
            }
            Text(
                text = stressResult,
                modifier = Modifier.testTag(LocalCallbackDemoTags.STRESS_RESULT),
            )

            SystemApiBenchmarkCard(
                running = systemBenchmarkRunning,
                status = systemBenchmarkStatus,
                results = systemBenchmarkResults,
                onRun = controller::runSystemApiBenchmarks,
            )
        }
    }
}

@Composable
private fun SystemApiBenchmarkCard(
    running: Boolean,
    status: String,
    results: List<SystemCallbackBenchmarkResult>,
    onRun: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Real AAOS system API benchmark")
            Text(
                "So sánh 32 callback hệ thống trực tiếp với 32 consumer dùng chung một " +
                    "LocalCallback. Mỗi API chạy 5 vòng và báo p50/p95.",
            )
            Button(
                onClick = onRun,
                enabled = !running,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(LocalCallbackDemoTags.SYSTEM_BENCHMARK),
            ) {
                Text(if (running) "Đang chạy…" else "Run real system API suite")
            }
            Text(
                text = status,
                modifier = Modifier.testTag(LocalCallbackDemoTags.SYSTEM_BENCHMARK_STATUS),
            )
            results.forEach { result ->
                SystemApiBenchmarkResultRow(result)
            }
        }
    }
}

@Composable
private fun SystemApiBenchmarkResultRow(result: SystemCallbackBenchmarkResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text("${result.displayName} • ${result.automotiveUse}")
            if (result.failure != null) {
                Text("Không chạy được: ${result.failure}")
            } else {
                val direct = checkNotNull(result.direct)
                val local = checkNotNull(result.local)
                MetricRow(
                    "System API registrations",
                    "${direct.externalRegistrations} → ${local.externalRegistrations}",
                )
                MetricRow(
                    "Attach p50 (direct/local)",
                    "${direct.attachP50Millis.asMillis()} / ${local.attachP50Millis.asMillis()}",
                )
                MetricRow(
                    "Attach p95 (direct/local)",
                    "${direct.attachP95Millis.asMillis()} / ${local.attachP95Millis.asMillis()}",
                )
                MetricRow(
                    "Platform callbacks p50",
                    "${direct.platformCallbacksP50} / ${local.platformCallbacksP50}",
                )
                MetricRow(
                    "Consumer deliveries p50",
                    "${direct.consumerDeliveriesP50} / ${local.consumerDeliveriesP50}",
                )
                MetricRow(
                    "Process CPU p50",
                    "${direct.processCpuP50Millis}ms / ${local.processCpuP50Millis}ms",
                )
                MetricRow("Local drops (all rounds)", local.droppedEvents.toString())
            }
        }
    }
}

private fun Double.asMillis(): String = "%.3fms".format(this)

@Composable
private fun DemoMetricsCard(
    status: String,
    activeSubscribers: Int,
    sourceStats: DemoSourceStats,
    sourceEvents: Long,
    deliveries: Long,
    drops: Long
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Shared-source metrics / Số liệu nguồn dùng chung")
            MetricRow("Status / Trạng thái", status)
            MetricRow(
                "Active local subscribers",
                activeSubscribers.toString(),
                LocalCallbackDemoTags.ACTIVE_SUBSCRIBERS
            )
            MetricRow(
                "External registrations",
                sourceStats.registrations.toString(),
                LocalCallbackDemoTags.REGISTRATIONS
            )
            MetricRow(
                "External unregistrations",
                sourceStats.unregistrations.toString(),
                LocalCallbackDemoTags.UNREGISTRATIONS
            )
            MetricRow("Source events", sourceEvents.toString())
            MetricRow("Local deliveries", deliveries.toString())
            MetricRow("Drops", drops.toString())
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, tag: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(
            text = value,
            modifier = if (tag == null) Modifier else Modifier.testTag(tag),
        )
    }
}

@Composable
private fun ConsumerCard(
    values: Map<String, Int?>,
    deliveries: Map<String, Long>
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Consumers / Nơi sử dụng")
            DemoConsumerNames.forEach { name ->
                val tag = when (name) {
                    "A" -> LocalCallbackDemoTags.VALUE_A
                    "B" -> LocalCallbackDemoTags.VALUE_B
                    else -> LocalCallbackDemoTags.VALUE_C
                }
                MetricRow(
                    label = "$name • deliveries=${deliveries[name] ?: 0L}",
                    value = values[name]?.toString() ?: "—",
                    tag = tag
                )
            }
        }
    }
}

private class LocalCallbackDemoController(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val registry = LocalCallbackRegistry(
        defaultConfig = LocalCallbackConfig.state(stopTimeoutMillis = 0L)
    )
    private val source = DemoExpensiveSource(::touch)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val sequence = AtomicInteger()
    private val stressRunning = AtomicBoolean(false)
    private val mutableConsumerValues = MutableStateFlow<Map<String, Int?>>(
        DemoConsumerNames.associateWith { null }
    )
    private val mutableConsumerDeliveries = MutableStateFlow(
        DemoConsumerNames.associateWith { 0L }
    )
    private val mutableStressResult = MutableStateFlow(
        "Ready / Sẵn sàng"
    )
    private val mutableSystemBenchmarkRunning = MutableStateFlow(false)
    private val mutableSystemBenchmarkStatus =
        MutableStateFlow("Ready • emulator callbacks have not been measured yet")
    private val mutableSystemBenchmarkResults =
        MutableStateFlow<List<SystemCallbackBenchmarkResult>>(emptyList())
    private val mutableRevision = MutableStateFlow(0L)

    val callback: LocalCallback<Int> = registry.getOrCreate(
        key = LocalCallbackKey.create("demo-expensive-manager")
    ) { source }
    val sourceStats: StateFlow<DemoSourceStats> = source.stats
    val consumerValues: StateFlow<Map<String, Int?>> = mutableConsumerValues.asStateFlow()
    val consumerDeliveries: StateFlow<Map<String, Long>> =
        mutableConsumerDeliveries.asStateFlow()
    val stressResult: StateFlow<String> = mutableStressResult.asStateFlow()
    val systemBenchmarkRunning: StateFlow<Boolean> =
        mutableSystemBenchmarkRunning.asStateFlow()
    val systemBenchmarkStatus: StateFlow<String> =
        mutableSystemBenchmarkStatus.asStateFlow()
    val systemBenchmarkResults: StateFlow<List<SystemCallbackBenchmarkResult>> =
        mutableSystemBenchmarkResults.asStateFlow()
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    fun startConsumers() {
        DemoConsumerNames.forEach { name ->
            if (jobs[name]?.isActive == true) return@forEach
            jobs[name] = callback.subscribe(
                scope = scope,
                onEvent = { value ->
                    mutableConsumerValues.update { it + (name to value) }
                    mutableConsumerDeliveries.update {
                        it + (name to ((it[name] ?: 0L) + 1L))
                    }
                    touch()
                },
                onFailure = { failure ->
                    Log.e(TAG, "Consumer $name failed", failure)
                    touch()
                }
            )
        }
    }

    fun stopOneConsumer() {
        jobs.remove("B")?.cancel()
    }

    fun stopConsumers() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    fun emitOne() {
        scope.launch {
            if (source.awaitRegistration()) {
                source.emit(sequence.incrementAndGet())
            }
        }
    }

    fun emitBurst() {
        scope.launch {
            if (source.awaitRegistration()) {
                repeat(1_000) {
                    source.emit(sequence.incrementAndGet())
                }
            }
        }
    }

    fun runStress() {
        if (!stressRunning.compareAndSet(false, true)) return
        mutableStressResult.value = "RUNNING / ĐANG CHẠY"
        scope.launch {
            val subscriberCount = 256
            val eventCount = 4_000
            val expected = subscriberCount.toLong() * eventCount
            val stressSource = DemoExpensiveSource()
            val stressRegistry = LocalCallbackRegistry()
            val stressCallback = stressRegistry.getOrCreate(
                key = LocalCallbackKey.create("demo-stress"),
                config = LocalCallbackConfig.events(
                    sourceBufferCapacity = eventCount,
                    subscriberBufferCapacity = eventCount
                )
            ) { stressSource }
            val observed = AtomicLong()
            try {
                val collectors = List(subscriberCount) {
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        stressCallback.events.take(eventCount).collect {
                            observed.incrementAndGet()
                        }
                    }
                }
                withTimeout(STRESS_TIMEOUT_MILLIS) {
                    while (
                        stressCallback.lifecycle.value.subscriberCount != subscriberCount ||
                        !stressCallback.lifecycle.value.isRegistered
                    ) {
                        delay(1)
                    }
                }
                val durationMillis = measureTimeMillis {
                    repeat(eventCount, stressSource::emit)
                    withTimeout(STRESS_TIMEOUT_MILLIS) {
                        collectors.forEach { it.join() }
                    }
                }
                val snapshot = stressCallback.snapshot()
                val passed = observed.get() == expected &&
                    snapshot.enqueuedDeliveries == expected &&
                    snapshot.droppedSourceEvents == 0L &&
                    snapshot.droppedDeliveries == 0L &&
                    stressSource.stats.value.registrations == 1
                val result = if (passed) {
                    "PASS"
                } else {
                    "FAIL"
                }
                mutableStressResult.value =
                    "$result • $subscriberCount × $eventCount = ${observed.get()} " +
                    "deliveries • ${durationMillis}ms • register=" +
                    stressSource.stats.value.registrations
                Log.i(
                    TAG,
                    "LOCAL_CALLBACK_STRESS $result subscribers=$subscriberCount " +
                        "events=$eventCount deliveries=${observed.get()} " +
                        "expected=$expected durationMs=$durationMillis " +
                        "registrations=${stressSource.stats.value.registrations} " +
                        "drops=${snapshot.droppedSourceEvents + snapshot.droppedDeliveries}"
                )
            } catch (failure: Throwable) {
                mutableStressResult.value =
                    "FAIL • ${failure::class.simpleName}: ${failure.message}"
                Log.e(TAG, "LOCAL_CALLBACK_STRESS FAIL", failure)
            } finally {
                stressRegistry.close()
                stressRunning.set(false)
                touch()
            }
        }
    }

    fun runSystemApiBenchmarks() {
        if (!mutableSystemBenchmarkRunning.compareAndSet(expect = false, update = true)) return
        mutableSystemBenchmarkResults.value = emptyList()
        mutableSystemBenchmarkStatus.value =
            "RUNNING • 7 APIs × direct/local × 5 measured rounds"
        scope.launch {
            try {
                SystemCallbackBenchmarkRunner(appContext).use { runner ->
                    val results =
                        runner.runAll { result ->
                            mutableSystemBenchmarkResults.update { it + result }
                            mutableSystemBenchmarkStatus.value =
                                "RUNNING • ${mutableSystemBenchmarkResults.value.size}/" +
                                    "${runner.scenarios.size} • ${result.displayName}"
                        }
                    val passed = results.count { it.failure == null }
                    mutableSystemBenchmarkStatus.value =
                        "DONE • $passed/${results.size} APIs measured • " +
                            "see log tag SystemCallbackBenchmark"
                }
            } catch (failure: Throwable) {
                mutableSystemBenchmarkStatus.value =
                    "FAIL • ${failure::class.simpleName}: ${failure.message}"
                Log.e(TAG, "System callback benchmark suite failed", failure)
            } finally {
                mutableSystemBenchmarkRunning.value = false
                touch()
            }
        }
    }

    override fun close() {
        stopConsumers()
        registry.close()
        scope.cancel()
    }

    private fun touch() {
        mutableRevision.update { it + 1L }
    }

    private companion object {
        private const val TAG = "BMaterialLocalCallbackDemo"
        private const val STRESS_TIMEOUT_MILLIS = 60_000L
    }
}

private data class DemoSourceStats(
    val registrations: Int = 0,
    val unregistrations: Int = 0,
    val emitted: Long = 0L,
    val isRegistered: Boolean = false
)

private class DemoExpensiveSource(
    private val onChanged: () -> Unit = {}
) : LocalCallbackSource<Int> {
    private val emitter = AtomicReference<LocalCallbackEmitter<Int>?>(null)
    private val mutableStats = MutableStateFlow(DemoSourceStats())

    val stats: StateFlow<DemoSourceStats> = mutableStats.asStateFlow()

    override fun register(emitter: LocalCallbackEmitter<Int>): LocalCallbackRegistration {
        check(this.emitter.compareAndSet(null, emitter)) {
            "Demo source cannot have multiple active registrations"
        }
        mutableStats.update {
            it.copy(registrations = it.registrations + 1, isRegistered = true)
        }
        onChanged()
        val closed = AtomicBoolean(false)
        return LocalCallbackRegistration {
            if (closed.compareAndSet(false, true)) {
                this.emitter.compareAndSet(emitter, null)
                mutableStats.update {
                    it.copy(
                        unregistrations = it.unregistrations + 1,
                        isRegistered = false
                    )
                }
                onChanged()
            }
        }
    }

    fun emit(value: Int): Boolean {
        val target = emitter.get() ?: return false
        mutableStats.update { it.copy(emitted = it.emitted + 1L) }
        target.emit(value)
        onChanged()
        return true
    }

    suspend fun awaitRegistration(timeoutMillis: Long = 2_000L): Boolean {
        return runCatching {
            withTimeout(timeoutMillis) {
                while (emitter.get() == null) {
                    delay(1)
                }
                true
            }
        }.getOrDefault(false)
    }
}

internal object LocalCallbackDemoTags {
    const val ROOT = "local_callback_demo"
    const val START = "local_callback_start"
    const val EMIT = "local_callback_emit"
    const val BURST = "local_callback_burst"
    const val STOP_ONE = "local_callback_stop_one"
    const val STOP_ALL = "local_callback_stop_all"
    const val STRESS = "local_callback_stress"
    const val STRESS_RESULT = "local_callback_stress_result"
    const val SYSTEM_BENCHMARK = "local_callback_system_benchmark"
    const val SYSTEM_BENCHMARK_STATUS = "local_callback_system_benchmark_status"
    const val REGISTRATIONS = "local_callback_source_registration_count"
    const val UNREGISTRATIONS = "local_callback_source_unregistration_count"
    const val ACTIVE_SUBSCRIBERS = "local_callback_active_subscribers"
    const val VALUE_A = "local_callback_a_value"
    const val VALUE_B = "local_callback_b_value"
    const val VALUE_C = "local_callback_c_value"
}

private val DemoConsumerNames = listOf("A", "B", "C")

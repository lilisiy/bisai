package com.example.bisai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.ListItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.ExperimentalFoundationApi
import com.example.bisai.ui.theme.AppTheme
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val auth = remember { AuthRepository(this) }
                var screen by remember { mutableStateOf(if (auth.isLoggedIn()) Screen.Home else Screen.Login) }
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (screen) {
                        Screen.Login -> LoginScreen(
                            onLoginSuccess = { screen = Screen.Home },
                            onNavigateToRegister = { screen = Screen.Register },
                            authRepository = auth
                        )
                        Screen.Register -> RegisterScreen(
                            onRegistered = { screen = Screen.Login },
                            onBackToLogin = { screen = Screen.Login },
                            authRepository = auth
                        )
                        Screen.Home -> HomeScreen(
                            onLogout = {
                                auth.setLoggedIn(false)
                                screen = Screen.Login
                            }
                        )
                    }
                }
            }
        }
    }
}

private enum class Screen { Login, Register, Home }

private class AuthRepository(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun register(username: String, password: String): Result<Unit> {
        if (username.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Username and password required"))
        }
        val existing = prefs.getString("user_$username", null)
        if (existing != null) return Result.failure(IllegalStateException("User already exists"))
        prefs.edit().putString("user_$username", password).apply()
        return Result.success(Unit)
    }

    fun login(username: String, password: String): Boolean {
        val stored = prefs.getString("user_$username", null) ?: return false
        val ok = stored == password
        if (ok) setLoggedIn(true)
        return ok
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean("isLoggedIn", false)
    fun setLoggedIn(value: Boolean) { prefs.edit().putBoolean("isLoggedIn", value).apply() }
}

@Composable
private fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    authRepository: AuthRepository
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun doLogin() {
        error = null
        if (authRepository.login(username.trim(), password)) {
            onLoginSuccess()
        } else {
            error = "用户名或密码错误"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("登录", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("用户名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(text = error!!, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { doLogin() }, modifier = Modifier.fillMaxWidth()) {
            Text("登 录")
        }
        TextButton(onClick = onNavigateToRegister) { Text("注册新账号") }
    }
}

@Composable
private fun RegisterScreen(
    onRegistered: () -> Unit,
    onBackToLogin: () -> Unit,
    authRepository: AuthRepository
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    fun doRegister() {
        error = null
        success = null
        if (password != confirm) {
            error = "两次输入的密码不一致"
            return
        }
        val result = authRepository.register(username.trim(), password)
        result.onSuccess {
            success = "注册成功，请返回登录"
        }.onFailure { e ->
            error = e.message ?: "注册失败"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("注册", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("用户名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("确认密码") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(text = error!!, color = MaterialTheme.colorScheme.error)
        }
        if (success != null) {
            Spacer(Modifier.height(8.dp))
            Text(text = success!!, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { doRegister() }, modifier = Modifier.fillMaxWidth()) {
            Text("注 册")
        }
        TextButton(onClick = onBackToLogin) { Text("返回登录") }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(onLogout: () -> Unit) {
    // --- Bafa Cloud 客户端 ---
    val bafaClient = remember { BafaClient(uid = BafaDefaults.UID) }
    val subscribeTopics = remember {
        listOf(
            BafaDefaults.TOPIC_DISTANCE, BafaDefaults.TOPIC_DISTANCE_DOWN, BafaDefaults.TOPIC_DISTANCE_UP
        )
    }

    // 摄像头 IP 地址
    var cameraIp by rememberSaveable { mutableStateOf("192.168.4.1") }
    var showCameraSettings by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()

    // 距离
    var distance by rememberSaveable { mutableStateOf("") }
    var distanceMin by rememberSaveable { mutableStateOf("") } // Input
    var distanceMax by rememberSaveable { mutableStateOf("") } // Input
    var distanceMinLimit by rememberSaveable { mutableStateOf("") } // Effective
    var distanceMaxLimit by rememberSaveable { mutableStateOf("") } // Effective
    val distanceSeries = remember { mutableStateListOf<Float>() }

    var alertMessage by remember { mutableStateOf<String?>(null) }
    var confirmedAlertKeys by remember { mutableStateOf(emptySet<String>()) }
    var currentActiveAlertKeys by remember { mutableStateOf(emptySet<String>()) }

    // 发布阈值（仅当 value 是合法数字时），并打印日志
    fun upload(topic: String, value: String) {
        val v = value.trim()
        if (v.isEmpty()) return
        if (v.toDoubleOrNull() == null) {
            Log.w("Bafa", "Skip upload, invalid number for $topic: '$v'")
            return
        }
        Log.d("Bafa", "PUBLISH threshold to $topic: $v")
        bafaClient.publish(topic, v)
    }

    // 防止正在编辑时被服务端值覆盖：记录哪些主题处于“本地编辑中”
    val dirtyTopics = remember { mutableStateMapOf<String, Boolean>() }
    fun markDirty(topic: String) { dirtyTopics[topic] = true }
    fun applyFromServer(topic: String, incoming: String, current: String, set: (String) -> Unit) {
        val isDirty = dirtyTopics[topic] == true
        if (isDirty && incoming != current) return
        set(incoming)
        // 收到与当前一致的服务端值，认为已同步，解除锁定
        if (incoming == current) dirtyTopics.remove(topic)
    }

    // 启动客户端并订阅主题
    LaunchedEffect(Unit) {
        // 由客户端在连接成功后自动分批订阅并逐个拉取一条历史
        bafaClient.start(BafaClient.Config(topics = subscribeTopics, pullHistoryOnStart = true))
    }

    DisposableEffect(Unit) {
        onDispose { bafaClient.stop() }
    }

    // 收到消息后写入对应的 UI 状态
    val latestMap by bafaClient.latestValues.collectAsState()
    LaunchedEffect(latestMap) {
        // --- 距离 ---
        latestMap[BafaDefaults.TOPIC_DISTANCE]?.let { v ->
            distance = v
            v.toFloatOrNull()?.let {
                distanceSeries.add(it)
                if (distanceSeries.size > 150) distanceSeries.removeAt(0)
            }
        }
        latestMap[BafaDefaults.TOPIC_DISTANCE_DOWN]?.let { v -> 
            distanceMinLimit = v
            applyFromServer(BafaDefaults.TOPIC_DISTANCE_DOWN, v, distanceMin) { distanceMin = it } 
        }
        latestMap[BafaDefaults.TOPIC_DISTANCE_UP]?.let { v -> 
            distanceMaxLimit = v
            applyFromServer(BafaDefaults.TOPIC_DISTANCE_UP, v, distanceMax) { distanceMax = it } 
        }

        // 报警检测 (使用 Limit 变量，基于 Key 进行去重)
        val alerts = mutableListOf<String>()
        val alertKeys = mutableSetOf<String>()

        fun check(valStr: String, minStr: String, maxStr: String, name: String, unit: String, keyPrefix: String) {
            val v = valStr.toFloatOrNull() ?: return
            val min = minStr.toFloatOrNull()
            val max = maxStr.toFloatOrNull()
            if (min != null && v < min) {
                alerts.add("$name ($v$unit) 低于下限 ($min$unit)！")
                alertKeys.add("${keyPrefix}_low")
            }
            if (max != null && v > max) {
                alerts.add("$name ($v$unit) 超过上限 ($max$unit)！")
                alertKeys.add("${keyPrefix}_high")
            }
        }

        check(distance, distanceMinLimit, distanceMaxLimit, "距离", "cm", "distance")

        // 更新当前的活跃报警 Key 集合，供确认按钮使用
        currentActiveAlertKeys = alertKeys

        val resolved = confirmedAlertKeys - alertKeys
        if (resolved.isNotEmpty()) {
            confirmedAlertKeys = confirmedAlertKeys - resolved
        }

        val newAlerts = alertKeys - confirmedAlertKeys
        if (newAlerts.isNotEmpty()) {
            alertMessage = alerts.joinToString("\n")
        } else {
            if (alertKeys.isEmpty()) {
                alertMessage = null
            } else if (alertMessage != null) {
                alertMessage = alerts.joinToString("\n")
            }
        }
    }

    var showHistory by remember { mutableStateOf(false) }

    val tabs = listOf("距离", "画面打开")
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("倒车影像", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Filled.History, contentDescription = "历史")
                    }
                    IconButton(onClick = { showCameraSettings = true }) {
                        Text("📷", style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Filled.Logout, contentDescription = "退出")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                edgePadding = 16.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) }
                    )
                }
            }
            
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> PageContent {
                        MonitorCard(
                            title = "距离", icon = "📏", value = distance, unit = "cm",
                            series = distanceSeries, color = Color(0xFF4CAF50),
                            min = distanceMin, max = distanceMax,
                            onMinChange = { distanceMin = it; markDirty(BafaDefaults.TOPIC_DISTANCE_DOWN) },
                            onMinUpload = { upload(BafaDefaults.TOPIC_DISTANCE_DOWN, distanceMin) },
                            onMaxChange = { distanceMax = it; markDirty(BafaDefaults.TOPIC_DISTANCE_UP) },
                            onMaxUpload = { upload(BafaDefaults.TOPIC_DISTANCE_UP, distanceMax) }
                        )
                    }
                    1 -> CameraStreamView(ipAddress = cameraIp)
                }
            }
        }
        if (showHistory) {
            HistoryDialog(
                bafaClient = bafaClient,
                onClose = { showHistory = false },
                refreshAll = {
                    val sensors = listOf(
                        BafaDefaults.TOPIC_DISTANCE
                    )
                    scope.launch {
                        sensors.forEach { t ->
                            bafaClient.requestHistoryOnce(t)
                            kotlinx.coroutines.delay(60)
                        }
                    }
                }
            )
        }

        if (showCameraSettings) {
            var ipInput by remember { mutableStateOf(cameraIp) }
            AlertDialog(
                onDismissRequest = { showCameraSettings = false },
                confirmButton = {
                    TextButton(onClick = {
                        cameraIp = ipInput.trim()
                        showCameraSettings = false
                    }) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { showCameraSettings = false }) { Text("取消") }
                },
                title = { Text("摄像头地址设置") },
                text = {
                    Column {
                        Text(
                            "请输入 ESP32-CAM 的 IP 地址：",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "• 直连 ESP32 热点：192.168.4.1\n• 同路由器下：路由器分配给 ESP32 的 IP",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = ipInput,
                            onValueChange = { ipInput = it },
                            label = { Text("IP 地址") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            )
        }

        if (alertMessage != null) {
            AlertDialog(
                onDismissRequest = { /* 禁止点击外部关闭，强制用户确认 */ },
                confirmButton = {
                    TextButton(onClick = { 
                        confirmedAlertKeys = confirmedAlertKeys + currentActiveAlertKeys
                        alertMessage = null 
                    }) { Text("确定") }
                },
                title = { Text("环境报警", color = MaterialTheme.colorScheme.error) },
                text = { Text(alertMessage!!) },
                icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
            )
        }
    }
}

@Composable
private fun MonitorCard(
    title: String,
    icon: String,
    value: String,
    unit: String,
    series: List<Float>,
    color: Color,
    min: String,
    max: String,
    onMinChange: (String) -> Unit,
    onMinUpload: () -> Unit,
    onMaxChange: (String) -> Unit,
    onMaxUpload: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = icon, style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                    Text(
                        text = if (value.isEmpty()) "-- $unit" else "$value $unit",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            LineChart(points = series, color = color)
            Spacer(Modifier.height(24.dp))
            RangeFields(
                minValue = min, onMinChange = onMinChange, onMinUpload = onMinUpload,
                maxValue = max, onMaxChange = onMaxChange, onMaxUpload = onMaxUpload
            )
        }
    }
}

@Composable
fun CameraStreamView(ipAddress: String) {
    var isError by remember { mutableStateOf(false) }
    val url = "http://${ipAddress}/stream"
    val context = LocalContext.current

    if (isError) {
        // 连接失败时显示友好提示
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "无法连接到 ESP32-CAM",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "请确认：\n1. ESP32-CAM 已上电\n2. 手机已连接正确 WiFi\n3. IP 地址正确: $ipAddress",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { isError = false }) {
                Text("重试连接")
            }
        }
    } else {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // 防止WebView在App内打开新页面
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            return true
                        }
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            isError = true
                        }
                    }
                    loadUrl(url)
                }
            },
            update = { webView ->
                // 当 isError 变为 false 时，重新加载
                webView.loadUrl(url)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun PageContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        content()
    }
}

@Composable
private fun RangeFields(
    minValue: String,
    onMinChange: (String) -> Unit,
    onMinUpload: () -> Unit,
    maxValue: String,
    onMaxChange: (String) -> Unit,
    onMaxUpload: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = minValue,
            onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) onMinChange(v) },
            label = { Text("下限报警值") },
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = onMinUpload,
                    enabled = minValue.trim().toDoubleOrNull() != null
                ) { Icon(Icons.Filled.Send, contentDescription = "发送下限") }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )
        OutlinedTextField(
            value = maxValue,
            onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) onMaxChange(v) },
            label = { Text("上限报警值") },
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = onMaxUpload,
                    enabled = maxValue.trim().toDoubleOrNull() != null
                ) { Icon(Icons.Filled.Send, contentDescription = "发送上限") }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )
    }
}

@Composable
private fun LineChart(
    points: List<Float>,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier.fillMaxWidth().height(160.dp),
    fixedMin: Float? = null,
    fixedMax: Float? = null
) {
    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas
        var minV = fixedMin ?: (points.minOrNull() ?: 0f)
        var maxV = fixedMax ?: (points.maxOrNull() ?: 1f)
        if (fixedMin == null && fixedMax == null && maxV <= minV) {
            minV = minV - 0.5f
            maxV = maxV + 0.5f
        } else if (maxV <= minV) {
            maxV = minV + 1f
        }
        val stepX = if (points.size <= 1) size.width else size.width / (points.size - 1).toFloat()
        fun fy(vIn: Float): Float {
            val v = vIn.coerceIn(minV, maxV)
            return size.height - ((v - minV) / (maxV - minV)) * size.height
        }
        val path = Path()
        path.moveTo(0f, fy(points[0]))
        for (i in 1 until points.size) {
            path.lineTo(i * stepX, fy(points[i]))
        }
        drawPath(path = path, color = color, style = Stroke(width = 3f))
        for (i in points.indices) {
            val x = if (points.size <= 1) 0f else i * stepX
            val y = fy(points[i])
            drawCircle(color = color, radius = 5f, center = Offset(x, y))
        }
    }
}

@Composable
private fun HistoryDialog(
    bafaClient: BafaClient,
    onClose: () -> Unit,
    refreshAll: () -> Unit,
) {
    val historyMap by bafaClient.historyByTopic.collectAsState()
    val tabs = listOf(
        "距离" to BafaDefaults.TOPIC_DISTANCE
    )
    var idx by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onClose) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("历史记录", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = refreshAll) { Text("刷新") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onClose) { Text("关闭") }
                }
                Spacer(Modifier.height(8.dp))
                ScrollableTabRow(selectedTabIndex = idx, edgePadding = 12.dp) {
                    tabs.forEachIndexed { i, pair ->
                        Tab(
                            selected = idx == i,
                            onClick = { idx = i },
                            text = { Text(pair.first) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                val topic = tabs[idx].second
                val list = (historyMap[topic] ?: emptyList()).asReversed()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(list) { e ->
                        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                            .format(java.util.Date(e.timestamp))
                        ListItem(
                            headlineContent = { Text(e.msg) },
                            supportingContent = { Text(time, fontFamily = FontFamily.Monospace) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

# FRPC集成使用指南

## 已完成功能

### 后端实现 (100%完成)
- ✅ **FrpcConfig**: 配置类，支持加密存储
- ✅ **FrpcManager**: 进程管理、WiFi绑定、输出监控
- ✅ **FrpcService**: 前台服务、通知系统
- ✅ **NetworkManager**: WiFi网络检测和绑定
- ✅ **frpc二进制**: v0.65.0 (arm64-v8a, armeabi-v7a, x86_64)

### UI资源 (已完成)
- ✅ 40+中文字符串资源
- ✅ FRPC配置对话框布局 (`dialog_frpc_config.xml`)

## 待完成工作

### MainActivity集成 (需手动完成)

由于FRPC功能是独立于主代理服务的，建议以下集成方式：

#### 方案1: 简单集成（推荐）
在MainActivity中添加最小化的FRPC控制：

```kotlin
// 在MainActivity中添加
  
private var frpcService: FrpcService? = null
private var frpcBound = false

private val frpcConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val binder = service as FrpcService.FrpcBinder
        frpcService = binder.getService()
        frpcBound = true
        updateFrpcUI()
    }
    
    override fun onServiceDisconnected(name: ComponentName?) {
        frpcService = null
        frpcBound = false
    }
}

// 在onCreate中绑定服务
Intent(this, FrpcService::class.java).also { intent ->
    bindService(intent, frpcConnection, Context.BIND_AUTO_CREATE)
}

// FRPC控制方法
private fun toggleFrpc() {
    if (frpcService?.isRunning() == true) {
        stopFrpcService()
    } else {
        startFrpcService()
    }
}

private fun startFrpcService() {
    val intent = Intent(this, FrpcService::class.java).apply {
        action = FrpcService.ACTION_START
    }
    startService(intent)
}

private fun stopFrpcService() {
    val intent = Intent(this, FrpcService::class.java).apply {
        action = FrpcService.ACTION_STOP
    }
    startService(intent)
}

private fun showFrpcConfigDialog() {
    // 使用 dialog_frpc_config.xml 创建对话框
    val dialogView = layoutInflater.inflate(R.layout.dialog_frpc_config, null)
    val config = FrpcConfig.load(this)
    
    // 初始化dialog中的视图...
    
    AlertDialog.Builder(this)
        .setTitle(R.string.frpc_config_title)
        .setView(dialogView)
        .show()
}
```

#### 方案2: 创建独立Activity
为FRPC创建独立的配置和控制Activity：
- `FrpcActivity.kt` - 专门的FRPC控制界面
- 从MainActivity通过菜单或按钮跳转

## 配置示例

```kotlin
val config = FrpcConfig(
    serverAddr = "your.server.com",
    serverPort = 7000,
    authToken = "your_token_here",
    tlsEnable = true,
    proxyType = ProxyType.STCP,
    proxyName = "CellularRouter",
    secretKey = "your_secret_key",
    localIP = "127.0.0.1",
    localPort = 1080,  // 与ProxyService的端口同步
    useCompression = true,
    useEncryption = true
)

config.save(context)
```

## 测试步骤

### 1. 配置FRPC
1. 确保WiFi已连接
2. 打开FRPC配置对话框
3. 填写服务器地址、端口、token等
4. 保存配置

### 2. 启动服务
1. 先启动主代理服务（蜂窝网络）
2. 再启动FRPC服务（WiFi网络）
3. 检查通知栏中的两个服务状态

### 3. 验证连接
1. 在frp服务器端查看客户端连接日志
2. 使用frpc stcp/xtcp的访问者端连接
3. 验证流量是否正确路由

## 技术要点

### 网络隔离
- **ProxyService**: 绑定到蜂窝网络
- **FrpcService**: 绑定到WiFi网络
- 两个服务完全独立，可单独启停

### 配置安全
- 使用`EncryptedSharedPreferences`加密存储
- authToken和secretKey不会以明文保存
- 配置文件权限设为私有

### 进程管理
- frpc作为`libfrpc.so`嵌入APK
- 通过ProcessBuilder执行
- 自动监控输出和退出状态
- 支持`loginFailExit=false`自动重连

## 故障排查

### FRPC启动失败
- 检查WiFi是否连接
- 检查配置是否有效
- 查看logcat中的FRPC输出

### DNS解析失败
- 在配置中指定DNS服务器（默认8.8.8.8）
- arm64架构使用Android类型frpc内核

### Process被杀
- 确保FrpcService作为前台服务运行
- 添加应用到电池优化白名单

## 文件位置

- 配置类: `app/src/main/java/com/cellularrouter/frpc/FrpcConfig.kt`
- 管理器: `app/src/main/java/com/cellularrouter/frpc/FrpcManager.kt`
- 服务: `app/src/main/java/com/cellularrouter/frpc/FrpcService.kt`
- 二进制: `app/src/main/jniLibs/[abi]/libfrpc.so`
- 对话框: `app/src/main/res/layout/dialog_frpc_config.xml`
- 字符串: `app/src/main/res/values/strings.xml`

##下一步计划

1. 完成MainActivity的FRPC集成（可选择方案1或方案2）
2. 测试完整流程
3. 根据实际使用调整UI
4. 更新README文档

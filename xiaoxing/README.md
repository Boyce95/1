# 平板端 App（小星助手）

这是路径 B 的平板端组件。它只负责三件事：**录音 → 播放 → 执行云端下发的设备动作**。所有算力（识别、理解、合成）都在云端服务，你的电脑可以完全关机。

## 工作流程

```
按住"说话" → 录音 → 上传云端 /api/agent-voice
                          ↓
              云端：ASR 识别 → 千问理解 → 返回 {回答, 动作列表}
                          ↓
        平板：显示并朗读回答 → 本地执行动作（root input 命令）
```

关键点：**ADB 动作在平板本地执行**（`ActionExecutor.java`），因为云端服务器碰不到你的平板。N777 已 ROOT，所以点击/按键/启动应用都能做。

## 一、云打包（推荐，无需安装 Android Studio）

打包工作流已经内置在本目录的 `.github/workflows/build.yml`，**不需要你手写，也不需要 gradlew 或 gradle-wrapper.jar**——工作流会自动下载匹配的 Gradle 5.4.1、安装 Android SDK 28 并构建。

1. 在 GitHub 新建一个仓库（比如 `xiaoxing-assistant`）。
2. 把本目录 `android-app/` 下的**全部内容**上传到仓库根目录，注意包括隐藏的 `.github` 文件夹。
   - 网页拖拽时隐藏文件夹容易被漏掉，最稳妥用命令行：

   ```bash
   cd android-app
   git init
   git add -A
   git commit -m "first commit"
   git remote add origin https://github.com/你的用户名/xiaoxing-assistant.git
   git push -u origin main
   ```

3. 打开仓库的 **Actions** 页面，确认 `Build Xiaoxing APK` 正在运行，等它变绿。
4. 点进这次运行，在页面底部 **Artifacts** 下载 `xiaoxing-apk`，解压得到 `app-debug.apk`。
5. 把 APK 传到平板安装（数据线拷贝，或发到网盘用平板浏览器下载）。

> 若没有自动触发，进入 Actions 页面点 **Run workflow** 手动跑一次。
> 构建失败先看日志，最常见原因是漏传了 `.github` 目录。

## 二、本地打包（有 Android Studio）

1. 安装 Android Studio，打开本目录 `android-app/`。
2. 首次打开会提示下载 Gradle，等它完成。
3. 菜单 Build → Build Bundle(s)/APK(s) → Build APK(s)。
4. APK 在 `app/build/outputs/apk/debug/app-debug.apk`。

## 三、安装与配置

1. 平板开启"未知来源应用安装"，安装 APK。
2. 打开 App，点右上角"设置"，填入云端服务地址：
   - 如果服务部署在公网：`https://你的域名`
   - 如果服务在局域网电脑：`http://电脑IP:8765`
3. 授予录音权限。
4. 按住"说话"按钮开始对话。

## 四、权限说明

- `INTERNET`：访问云端服务
- `RECORD_AUDIO`：语音输入
- `WRITE/READ_EXTERNAL_STORAGE`：保存录音和播放音频
- 设备控制依赖 **ROOT**（N777 已 ROOT 可用刷机精灵等工具获取）；无 root 时仅"启动应用"可用，点击/按键不可用。

## 五、安全

- 设备动作在端侧白名单校验（`ActionExecutor.java`），云端只能下发约定好的动作，无法执行任意命令。
- App 与云端通信走 HTTP；公网部署请务必配 HTTPS，避免 API 调用被窃听。

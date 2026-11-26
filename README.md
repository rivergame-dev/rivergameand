# River Dash · Android

在 Android Studio 中打开本目录，配置 JDK 17 和 Android SDK 35。选择 Android 6.0 或更新版本的模拟器或设备，点击 Run。

也可以在项目根目录执行 `./gradlew assembleDebug`，生成 `app/build/outputs/apk/debug/app-debug.apk`。

拖动小船控制方向，收集河灯得 10 分，撞到岩石结束一局。水流随时间加速，结算页可重新开始。最佳分数保存在本机。

工程将 `images/googleplay.png` 直接打包进应用，主菜单加载这张图片。

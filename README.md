# 火花助手 · Android 手机后台版

版本：0.6-flex-nightly。个人项目，不是抖音官方产品。

首次授权后在手机独立运行，计划每天北京时间22:00检查已识别的火花关系。只对官方当前状态明确待续的私聊尝试发送一个普通表情；已续、重燃中、未知、读取失败及当天已尝试的会话均跳过。发送前以账号/会话/北京时间日期持久化记录，发送后区分消息回执和官方点亮结果。

## 验证与限制

- 已通过单人真实表情发送、官方状态回读及原版应用人工核对。
- 已验证手机退到桌面后的后台运行、系统唤醒和只读批量演练。
- 35项Node测试、15项Java时间边界检查通过。
- 定时批量已登记，但首次自然触发的完整批次仍待观察。
- 会话扫描有分页上限，会合并既有名单和近期会话，不保证覆盖全部历史好友。
- 系统可能推迟任务。测试设备将22:00请求放入22:00–23:00弹性窗口；本版本不使用闹钟标记。
- 手机需开机联网。授权失效会停止任务，不会自动打开原版抖音重新登录。
- Qwen智能回复、陌生人回复和评论回复尚未接通。

## 目录与测试

`spark-engine/`包含Android源码、手机Node工作脚本、公开依赖及第三方许可；`background-streak-policy/`包含状态判断、去重、真实读取适配器和测试。

```sh
python spark-engine/build.py --test-only
```

## 构建

需要Python3.9+、Node.js、JDK、Android API36和Build Tools。设置JAVA_HOME、SPARK_ANDROID_JAR、SPARK_BUILD_TOOLS，可选SPARK_ECJ_JAR使用ECJ替代javac，然后执行：

```sh
python spark-engine/build.py
```

默认生成未签名APK。自行签名时设置SPARK_KEYSTORE、SPARK_KEYSTORE_PASSWORD，可选SPARK_KEY_ALIAS。签名材料不在仓库中；其他签名不能覆盖原签名安装包。

`vendor/runtime-arm64.zip.part*`（构建脚本自动合并）包含ARM64 Node.js Mobile18.20.4、官方NDKr24 libc++及JNI桥；`protocol.zip`来自MIT项目zhinjs/douyin-im固定提交f6eee669c39094224e00c6b877e1964d9da4e393及公开依赖，修改记录在压缩包内MOBILE-CHANGES.md。完整许可见licenses和THIRD_PARTY_NOTICES.txt。JNI桥源IR和构建脚本在native目录。

源码包不包含登录会话、用户账号数据、好友名单、API密钥、签名私钥、设备截图或日志。项目自有代码暂未指定开源许可证，第三方代码保持各自原许可。

## 安装包与历史版本

安装包以分卷存放，避免上传接口的大文件限制。下载[完整仓库ZIP](https://github.com/ruiyangxie434-byte/TikTok-spark-assistant/archive/refs/heads/main.zip)并解压，在仓库根目录执行：

```sh
python tools/unpack-apk.py
```

脚本会校验并生成 `artifacts/SparkBackground-0.6.apk`，不需要Android构建工具。校验值见[SHA256SUMS](artifacts/SHA256SUMS-0.6.txt)。

`spark-background/`和`artifacts/SparkBackground-check.apk`保留为0.3诊断历史；当前源码请使用`spark-engine/`。

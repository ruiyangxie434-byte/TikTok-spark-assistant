# 火花助手 · Android 排查原型

**版本：0.3-session-check。当前不能自动续火花，也没有启用每天 22:00 的任务。**

这是一个个人 Android 项目的阶段性源码快照，便于后续继续改进；不是抖音官方项目。

目标是在手机上独立运行：每天北京时间 22:00 检查现有火花好友，跳过当天已完成的会话，仅对已确认待续的会话尝试一次普通表情。该完整流程尚未实现和验收。

## 目前包含

- Android 连接诊断界面和仅使用助手自身旧会话的账号资料检查。
- 本地签名脚本、固定的官方 HTTPS 读取目标、禁止跳转及响应 Cookie 回写的原生读取。
- 保守的火花字段解析、北京时间日期与下一次 22:00 时间计算、账号和会话级去重决策；这些规则未连接到实际发送或调度服务。
- 22 项离线测试，覆盖规则、签名向量、未知响应和诊断结果不导出账号资料。
- 当前排查 APK：[`artifacts/SparkBackground-check.apk`](artifacts/SparkBackground-check.apk)。

## 尚未解决

- 助手网页登录后原版抖音可能被退出，尚未找到可靠的会话共存方案。
- 旧会话检查可能无法确认登录有效；未知响应不会触发自动重新登录。
- 手机后台运行、会话状态的新鲜度和完整覆盖仍未验收。
- 实际表情发送、22:00 Android 调度、重启恢复、发送后的官方状态确认尚未实现。
- 千问智能回复和评论区自动回复不在本次快照中。

网页登录与页面检查入口目前暂缓。详情见 [`docs/STATUS.md`](docs/STATUS.md)。

## 目录

```text
spark-background/          Android 源码、本地签名、测试、构建脚本及第三方许可
background-streak-policy/  不登录、不联网、不发送的独立规则模块
artifacts/                 当前排查 APK 与 SHA-256 校验值
docs/                      进度记录与网页上传说明
```

## 验证与构建

无需安装 npm 依赖。使用 Python 3 和支持 `node:test` 的 Node.js（本快照验证环境为 Python 3.13、Node.js 24、Java 21）：

```sh
python spark-background/build.py --test-only
```

编译 APK 还需要 JDK、Android API 36 的 `android.jar`、含 `aapt2`、`zipalign`、`lib/d8.jar` 与 `lib/apksigner.jar` 的 Android Build Tools。设置 `JAVA_HOME`、`SPARK_ANDROID_JAR`、`SPARK_BUILD_TOOLS` 后执行：

```sh
python spark-background/build.py
```

默认只生成 `artifacts/SparkBackground-unsigned.apk`，不能直接安装。自行签名时设置 `SPARK_KEYSTORE`、`SPARK_KEYSTORE_PASSWORD`，可选 `SPARK_KEY_ALIAS`；输出 `artifacts/SparkBackground-local.apk`。仓库不提供签名私钥、密码、账号凭据或 Android 工具链。自建签名不能直接覆盖使用其他证书安装的版本。

## 来源与许可证

第三方代码的来源、原有版权、Apache-2.0/MIT 许可和修改说明见 [`spark-background/THIRD_PARTY_NOTICES.txt`](spark-background/THIRD_PARTY_NOTICES.txt) 及 [`background-streak-policy/SOURCE.md`](background-streak-policy/SOURCE.md)。

项目自有代码暂未指定开源许可证；后续由仓库所有者决定。公开源码不改变第三方代码原有许可。

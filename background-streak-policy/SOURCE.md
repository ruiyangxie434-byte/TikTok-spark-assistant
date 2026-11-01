# 独立后台规则模块

`streak-policy.mjs`只解析传入数据并给出决策。当前目录还包含由调用者注入网络/发送能力的执行器、持久化尝试记录，以及只读服务端适配器；模块不自行登录或建立定时服务。

字段来源：MIT 项目 [ZhangYukwiver/DouyinDataReview](https://github.com/ZhangYukwiver/DouyinDataReview/blob/5367ebc02949e8e11f1fbf709116bae1133fe6a7/LICENSE)，固定提交 `5367ebc02949e8e11f1fbf709116bae1133fe6a7`。

- [chatViewer.mjs:10-36](https://github.com/ZhangYukwiver/DouyinDataReview/blob/5367ebc02949e8e11f1fbf709116bae1133fe6a7/collector/chatViewer.mjs#L10-L36) 读取 `coreInfo.ext["a:consecutive_chat_data"]`，解析 `flame_infos` 的 Unix 秒时间窗。
- [chatSparks.ts:21-45](https://github.com/ZhangYukwiver/DouyinDataReview/blob/5367ebc02949e8e11f1fbf709116bae1133fe6a7/src/domain/chatSparks.ts#L21-L45) 将当前时间窗的 `state` 解释为 1 已完成、2 待续、3 重燃中；4 不活跃。这里只借鉴字段与设计，未引入其登录或接口实现。

**协议字段不是抖音官方公开 API 保证；当前真机验证范围见 `docs/STATUS.md`。** 不以本机聊天记录、连续天数或最后聊天时间估算官方完成状态。坏数据、当前窗缺失或重叠均保持未知；3/4 不自动发送。

发送须由调用者明确提供本次读取成功、已有火花关系及尝试记录集合。仅当前窗为 2 且该账号、会话、北京时间日期组合尚未尝试时允许。调用者须在发送前记录返回的 `key`；模块不会修改集合。读取失败时，即使提供旧数据，也返回未知并跳过。

`now` 参数采用有效 `Date`；解析器的 `nowSeconds` 为 Unix 秒。日期与计划使用固定北京时间 UTC+8，不依赖系统时区。`nextBeijing22` 返回严格晚于当前时刻的下一次 22:00；恰好 22:00 时返回次日，便于执行后重新安排。该函数不承担任务触发或补跑。

验证命令：`node --test background-streak-policy/streak-policy.test.mjs`。


## 0.7 当天已发送保护

`streak-runner.mjs` 在官方待续且尚未尝试时，额外要求 `readTodayActivity` 返回本次北京时间日期的结果。`self-sent` 跳过；只有 `not-sent` 可以进入发送前的持久化 claim；缺失、跨日或未知均不发送。此规则不估算官方火花状态，也不推断对方是否已回复。

`today-messages.mjs` 使用已固定版本的 MIT zhinjs/douyin-im：`src/services/im/inbox.ts` 的 `getMessages`、`mappers.ts` 的原始发送者与时间字段、`read-state.ts` 的系统提示/撤回过滤，以及 `sdk/events/assembler.ts` 的平台 UID / IM UID 自身识别思路。只调用历史读取，不调用已读、建会话或发送接口。只处理发送者、时间和记录类型，不保存消息正文。

本次真机只读演练确认了每页正序排列，以及 8 个你已发过消息但官方仍待续的会话均被跳过。接口不是抖音公开 API 保证，读取不完整时保持未知。当前真实验证以 `docs/STATUS.md` 和 `spark-engine/STATUS.md` 为准。

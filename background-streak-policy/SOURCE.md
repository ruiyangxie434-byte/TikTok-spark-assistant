# 独立后台规则模块

只解析传入数据并给出决策；不登录、不读取账号、不发送消息，也不包含存储或定时服务。

字段来源：MIT 项目 [ZhangYukwiver/DouyinDataReview](https://github.com/ZhangYukwiver/DouyinDataReview/blob/5367ebc02949e8e11f1fbf709116bae1133fe6a7/LICENSE)，固定提交 `5367ebc02949e8e11f1fbf709116bae1133fe6a7`。

- [chatViewer.mjs:10-36](https://github.com/ZhangYukwiver/DouyinDataReview/blob/5367ebc02949e8e11f1fbf709116bae1133fe6a7/collector/chatViewer.mjs#L10-L36) 读取 `coreInfo.ext["a:consecutive_chat_data"]`，解析 `flame_infos` 的 Unix 秒时间窗。
- [chatSparks.ts:21-45](https://github.com/ZhangYukwiver/DouyinDataReview/blob/5367ebc02949e8e11f1fbf709116bae1133fe6a7/src/domain/chatSparks.ts#L21-L45) 将当前时间窗的 `state` 解释为 1 已完成、2 待续、3 重燃中；4 不活跃。这里只借鉴字段与设计，未引入其登录或接口实现。

**这些字段含义尚未经过本项目的真实账号及真机验收，不属于抖音官方公开 API 保证。** 不以本机聊天记录、连续天数或最后聊天时间估算官方完成状态。坏数据、当前窗缺失或重叠均保持未知；3/4 不自动发送。

发送须由调用者明确提供本次读取成功、已有火花关系及尝试记录集合。仅当前窗为 2 且该账号、会话、北京时间日期组合尚未尝试时允许。调用者须在发送前记录返回的 `key`；模块不会修改集合。读取失败时，即使提供旧数据，也返回未知并跳过。

`now` 参数采用有效 `Date`；解析器的 `nowSeconds` 为 Unix 秒。日期与计划使用固定北京时间 UTC+8，不依赖系统时区。`nextBeijing22` 返回严格晚于当前时刻的下一次 22:00；恰好 22:00 时返回次日，便于执行后重新安排。该函数不承担任务触发或补跑。

验证命令：`node --test work/background-streak-policy/streak-policy.test.mjs`。

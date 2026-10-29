# 在 GitHub 网页上传

1. 在已登录的 Edge 打开 https://github.com/new 。仓库名填写 `douyin-spark-assistant`，可见性选择 Public。
2. 不额外创建 README、.gitignore 或 License（上传包已经包含前两项，自有代码许可证尚待决定），点击 Create repository。
3. 新空仓库页面点击 uploading an existing file；如果已经有文件，则选择 Add file → Upload files。
4. 解压上传包，打开到能看见 README.md 的这一层。把这一层的全部文件和子文件夹拖入网页，保留目录结构。不要只上传压缩包本身。
5. 等文件列表加载完，提交说明填写 `Save v0.3 session-check prototype`。选择提交到当前分支，再点击 Commit changes；若页面显示 Propose changes，则按页面创建分支/提交提示继续完成。
6. 上传后确认仓库首页能直接显示 README，并且能看到 spark-background、background-streak-policy、artifacts 和 docs 文件夹。

只上传此包内的内容，不要把原工作区的 work、outputs、手机截图或登录数据另外拖进去。

GitHub 官方说明：
https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-new-repository
https://docs.github.com/en/repositories/working-with-files/managing-files/adding-a-file-to-a-repository

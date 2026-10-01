# MioAI 项目约定

## Git 远程与推送

- 远程命名：
  - `origin` → GitHub：https://github.com/Takina610/MioAI.git
  - `gitee` → Gitee：https://gitee.com/takina610/mio-ai.git
- 本地分支只有 `master`：gitee 照常推 master；GitHub 的分支是 `main`，推送时由本地 master 转换过去（已配置 `remote.origin.push = master:main`，`git push origin` 自动完成转换）。两个远程都要推：

```bash
git push gitee master
git push origin        # 等价于 git push origin master:main
```

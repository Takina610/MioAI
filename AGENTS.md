# MioAI 项目约定

## Git 远程与推送

- 远程命名：
  - `origin` → GitHub：https://github.com/Takina610/MioAI.git
  - `gitee` → Gitee：https://gitee.com/takina610/mio-ai.git
- 分支统一用 `master`：gitee 的推送保持 master 不变；提交后转推 GitHub（origin），两个远程都要推：

```bash
git push gitee master
git push origin master
```

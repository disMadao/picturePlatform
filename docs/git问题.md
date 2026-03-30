习惯只用下面命令提交出现的大问题：

```cmd
git add .
git commit -m "asdf"
git push
```

某次commit的时候 把包含密钥的文件一起commit了。push失败，然后本地删掉了，继续重复上面命令。

之前的commit有也不行？那不是本地的的commit吗？

还涉及到一个 配置文件应该在 .gitignore里面。

弄了好久。



相当于学了一遍git



问题1：已经 add 的内容又不想上传了，只添加在 .gitignore中是没用的，需要先 git rm  --cached 

问题2：**修过了历史commit，就得强制提交**

这里的修改历史commit，使用的是 git rebase -i ，这种情况修改了某个历史的commit，就算是本地的没有push的，最后也得强制提交。




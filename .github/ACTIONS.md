# GitHub Actions 构建说明

本项目包含两个GitHub Actions工作流：

## 1. 手动构建Release APK

**工作流文件**: `.github/workflows/build-release.yml`

### 使用方法

1. 打开GitHub仓库页面
2. 点击 **Actions** 标签
3. 选择 **Build Release APK** 工作流
4. 点击 **Run workflow** 按钮
5. 输入版本名称（如: 1.0.0）
6. 点击 **Run workflow** 开始构建

### 构建产物

- **自动创建GitHub Release**: 版本tag为 `v{版本号}`
- **APK自动上传到Release**: 可直接从Release页面下载
- **Release说明**: 包含功能特性、系统要求等信息
- **Artifacts备份**: 同时上传到Actions Artifacts，保留30天

### 下载APK

构建完成后，可以从以下位置下载APK：
1. **GitHub Releases页面**（推荐）- 永久保存
2. Actions运行页面的Artifacts部分 - 保留30天

### 配置Release签名（可选）

如需使用自定义签名，需在GitHub仓库设置中添加以下Secrets：

```
KEYSTORE_BASE64: Keystore文件的Base64编码
KEYSTORE_PASSWORD: Keystore密码
KEY_ALIAS: 密钥别名
KEY_PASSWORD: 密钥密码
```

## 2. 自动构建Debug APK

**工作流文件**: `.github/workflows/build-debug.yml`

### 触发条件

- 推送到main分支
- Pull Request到main分支
- 手动触发

### 构建产物

- Debug APK自动上传
- 保留期: 7天
- 包含单元测试结果

## 本地生成Keystore（用于Release签名）

```bash
keytool -genkey -v -keystore cellular-router.keystore \
  -alias cellular-router -keyalg RSA -keysize 2048 \
  -validity 10000

# 转换为Base64以添加到GitHub Secrets
base64 cellular-router.keystore > keystore.txt
```

然后将keystore.txt的内容添加到GitHub Secrets的KEYSTORE_BASE64变量中。

## 常见问题

**Q: 为什么需要JDK 17?**
A: Android Gradle Plugin 8.2需要JDK 17或更高版本。

**Q: 构建失败怎么办?**
A: 查看Actions运行日志，检查Gradle同步和依赖问题。

**Q: 如何下载构建的APK?**
A: 在Actions运行页面，滚动到底部的Artifacts部分，点击下载。

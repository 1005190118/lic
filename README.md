# HzTech 机器码工具（源码 + 六平台自动打包）

使用 **Java 17 语法、OSHI 7.7.3、Maven、GitHub Actions 和 JDK 21 jpackage** 的离线软件 License 机器码生成器。

支持：

- Windows：x64 / ARM64
- Linux：x64 / ARM64
- macOS：Intel x64 / Apple Silicon ARM64
- 桌面图形界面：查看机器码、一键复制、重新读取
- 服务器命令行：输出一行机器码，方便粘贴或脚本读取
- 不含网络上报：所有计算在客户本机完成

> **重要：这套 V2 机器码算法与你原来 CPU ID + UUID 的 V1 算法不同。**
> 如果你的商业软件仍然使用旧版 `GetMachineUtil.getMachineKey()` 校验 License，
> 不能直接使用本工具的 V2 机器码签发新授权。生成端、签发端、校验端必须约定同一版本。

## 1. 下载源码并在本地运行

要求：JDK 21（JDK 17 也能编译运行）、Maven 3.9+、能访问 Maven Central。

```bash
mvn clean verify
java -jar target/hztech-machine-code.jar
```

有桌面的系统默认打开 GUI；无桌面环境自动进入命令行模式。

```bash
# 无 GUI，一行输出机器码（64 个十六进制字符）
java -jar target/hztech-machine-code.jar --cli

# 兼容原来 CPU ID + UUID 的算法，输出 V1 机器码
java -jar target/hztech-machine-code.jar --legacy

# 查看参数
java -jar target/hztech-machine-code.jar --help
```

仅为使用 **JAR 文件**需要安装 Java；下面的 `jpackage` 产物已内置运行时，客户不需要安装 Java。

## 2. 如何上传到 GitHub 并自动打包（最简单步骤）

1. 在 GitHub 新建一个仓库，例如 `hztech-machine-code`。建议先选择 **Private（私有）**，不要自动生成 README 等文件。
2. 解压本项目源码压缩包，将 **`pom.xml`、`src/`、`.github/`、`.gitignore` 等内容上传到仓库根目录**。注意 `.github/workflows/build.yml` 是隐藏目录里的工作流，必须一同上传。
3. 如果通过 Git 命令上传，在项目目录执行：

   ```bash
   git init
   git branch -M main
   git add .
   git commit -m "Initial machine code tool"
   git remote add origin https://github.com/你的用户名/hztech-machine-code.git
   git push -u origin main
   ```

4. 打开仓库 → **Actions** → 选择 **Build Machine Code (6 platforms)** → **Run workflow**。
5. 完成后点击该次运行，在底部 **Artifacts** 下载对应平台的压缩包。GitHub 可能再次将产物打成 ZIP；如果下载后还有一层 `.zip` / `.tar.gz`，再解压一次。

CI 包含一个单元测试任务和六个系统构建任务。`main` / `master` 分支 push 也会自动触发。

## 3. GitHub Actions 六种产物

| 平台 | Runner | 压缩文件 | 解压后的程序 |
|---|---|---|---|
| Windows x64 | `windows-2025` | `hztech-machine-code-windows-amd64.zip` | `HzTechMachineCode/HzTechMachineCode.exe` |
| Windows ARM64 | `windows-11-arm` | `hztech-machine-code-windows-arm64.zip` | `HzTechMachineCode/HzTechMachineCode.exe` |
| Linux x64 | `ubuntu-24.04` | `hztech-machine-code-linux-amd64.tar.gz` | `HzTechMachineCode/bin/HzTechMachineCode` |
| Linux ARM64 | `ubuntu-24.04-arm` | `hztech-machine-code-linux-arm64.tar.gz` | `HzTechMachineCode/bin/HzTechMachineCode` |
| macOS Intel | `macos-15-intel` | `hztech-machine-code-macos-amd64.tar.gz` | `HzTechMachineCode.app` |
| macOS Apple Silicon | `macos-15` | `hztech-machine-code-macos-arm64.tar.gz` | `HzTechMachineCode.app` |

**注意：**这里是 **jpackage app-image**，每个平台的文件夹 / `.app` 内置一个精简 Java 运行环境；**不是只有一个独立 EXE 文件**。请发送完整文件夹（或压缩包），不要只单独拷贝 `exe`。

- Windows GUI：双击 `HzTechMachineCode.exe`。需要命令行输出，可从 CMD / PowerShell 运行 `HzTechMachineCodeCLI.exe`。
- Linux：执行 `./HzTechMachineCode/bin/HzTechMachineCode --cli`，服务器无 GUI 会自动打印机器码；需要桌面则不加 `--cli`。
- macOS：双击 `HzTechMachineCode.app`，或在终端执行 `./HzTechMachineCode.app/Contents/MacOS/HzTechMachineCode --cli`。

macOS 构建产物**未签名/未公证**，Windows 也**未做代码签名**。正式对外分发建议进行签名与 macOS 公证；不能保证系统不弹安全警告，不应通过让客户全局关闭系统安全保护来解决。

Linux app-image 基于 GitHub Ubuntu Runner 制作，是否能在客户的 Linux 发行版正常运行取决于 glibc、GUI/X11、系统库等兼容条件；Alpine Linux（musl）、部分精简服务器系统可能需要额外测试。

## 4. 核心算法（V2）

1. 尝试 OSHI 读取硬件 UUID。只接受 32 个十六进制字符，兼容带连字符/不带连字符，过滤已知占位 UUID。
2. UUID 无效则读取**整机序列号**；仍无效则使用**主板序列号**。
3. 序列号忽略大小写与空白字符，过滤 `UNKNOWN`、`To Be Filled By O.E.M.` 等常见占位值。
4. 计算 `SHA-256("HZTECH|MACHINE|V2|" + 字段类型 + "|" + 规范化标识)`，输出 **64 位大写十六进制**。
5. 全部硬件标识无效则明确报错，**不会偷偷把 CPU 型号当唯一机器 ID**。

**边界：** OSHI 的 `processorID` 通常不是 CPU 唯一序列号。虚拟机复制、主板更换、系统 UUID 变化、容器隔离或权限限制都可能使机器码重复、变化或无法获取。若首次激活采用备用整机序列号，日后 UUID 变得可读，机器码也会变化；企业 License 需要对应的重绑定/迁移策略。

**安全提醒：**本机机器码不等于防破解功能，不应只用 `机器码相等` 判断授权合法。License 应由私钥签名，客户端仅存公钥。**签发私钥、客户密钥、生产凭据不要提交 GitHub**（包括私有仓库）。

## 5. 源码结构

```text
hztech-machine-code/
├── .github/workflows/build.yml                    # GitHub Actions 构建矩阵
├── .gitignore
├── pom.xml                                        # Maven + OSHI + JUnit + Shade
├── README.md
└── src/
    ├── main/java/org/hztech/core/plugin/license/common/util/
    │   ├── MachineCodeApp.java                    # GUI / CLI 入口
    │   ├── GetMachineUtil.java                    # OSHI 硬件信息读取；V1兼容
    │   └── MachineKeyGenerator.java               # 可单测的 V2 算法
    └── test/java/org/hztech/core/plugin/license/common/util/
        └── MachineKeyGeneratorTest.java
```

## 6. 常见问题

**GitHub Actions 会不会生成 GitHub 服务器的机器码？** 不会。CI 仅构建和执行 `--help` 自检。客户在自己电脑上运行程序时才读取客户机器的硬件信息。

**把源码放 GitHub 安全吗？** 本仓库不包含私钥、客户数据和接口密钥；公开源码也不会自动上报机器信息。但公开仓库所有人都能阅读你的机器码算法。若 License 核心项目尚未准备公开，建议使用私有仓库。

**Windows 打开 `.exe` 没有弹窗？** 优先从 PowerShell 运行 `HzTechMachineCodeCLI.exe` 查看是否能成功读取。或者安装 JDK 后运行 JAR 的 `--cli` 诊断。

**想保持原 V1 机器码不变？** 使用 `--legacy`，该模式对 CPU ID/UUID 使用未经 V2 规范化的原始拼接逻辑，输出应与原工具一致（前提是相同 OSHI 读到相同字符串）。现有授权校验端不能无迁移直接切换到 V2。

**可否将 `app-image` 改成 `.msi` / `.dmg`？** 可以，但涉及安装包构建工具、签名与更多平台限制。第一版先使用可解压直接运行的 app-image 便于验证。

## 7. 依赖与许可证

- OSHI (`oshi-core`): MIT 许可证。其他依赖按各自许可证发布；正式对外分发建议附上适用的第三方许可证通知。
- Java 打包运行环境：Eclipse Temurin JDK 21；请按相应许可证要求分发与提供必要通知。
- 本项目模板本身不预置开源许可证，仓库作者需自行决定是否公开授权。

*CI 与六平台产物配置已提供；不同宿主操作系统上的最终打包、签名和硬件识别行为仍需在 GitHub Actions 及真实设备上验证。*

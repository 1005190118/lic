# HzTech Machine Code — GraalVM Native Image 版

本仓库包含 **GraalVM Native Image 原生机器码工具**以及保留的 **JDK + OSHI/Swing GUI 版本**。机器码 V2 **不变**：`MachineKeyGenerator.generate(...)` 是唯一的 V2 算法实现。原始 V1 算法保留在 Java JAR 中，但 Native 版不包含 V1。

## 1. 六平台发布方案

| 系统 | 架构 | 发布产物 | 是否真正 GraalVM Native Image | 是否需安装 Java |
| --- | --- | --- | --- | --- |
| Windows | x64/AMD64 | `hztech-machine-code-windows-amd64-native.zip` | 是，单个 EXE | 否 |
| Linux | x64/AMD64 | `hztech-machine-code-linux-amd64-native.tar.gz` | 是，单个二进制 | 否 |
| Linux | ARM64 | `hztech-machine-code-linux-arm64-native.tar.gz` | 是，单个二进制 | 否 |
| macOS | Apple Silicon ARM64 | `hztech-machine-code-macos-arm64-native.tar.gz` | 是，单个二进制 | 否 |
| Windows | ARM64 | `hztech-machine-code-windows-arm64-jpackage.zip` | 否，保留 jpackage 兼容版 | 否（内置运行时） |
| macOS | Intel x64 | `hztech-machine-code-macos-amd64-jpackage.tar.gz` | 否，保留 jpackage 兼容版 | 否（内置运行时） |

> 原因：当前 GraalVM 没有官方 Windows ARM64 Native Image 发行版；当前最新维护中的 GraalVM 21 不支持 macOS x64。以上两种继续使用现有 jpackage，**不是原生单文件**。macOS 原生版不直接携带 Swing GUI，而是 CLI 二进制；可用 `--show` 通过 `osascript` 弹出系统对话框。Windows 原生 EXE 双击无参数会调用系统 PowerShell 显示可复制的机器码，命令行添加 `--cli` 只输出机器码。

## 2. GitHub 发布步骤

工作流 `.github/workflows/build.yml`：

- Push 到普通分支不会触发构建。
- 推送 `v1.0.0`、`v1.0.1` 等 `v*` Tag 会运行 Java 单元测试、四个平台 Native Image、两个平台 jpackage 兼容包；成功后发布 GitHub Release。
- 在 Actions 点击 `Run workflow` 手动运行仅构建，不发布 Release（从 Tag 手动运行时仍可能满足 Release 的 Tag 条件）。
- 构建任务会用固定 UUID 对比 JVM 与 Native Image 算法的 SHA-256 输出，确保计算规则一致；**不会从 CI 服务器读取真实机器码来做发布验证**。

```bash
# 确保仓库已提交新工作流及原生入口
 git add .
 git commit -m "Build GraalVM native executables"
 git push origin main

# 发布新版本，Tag 必须是未使用过的
 git tag v1.0.1
 git push origin v1.0.1
```

之后进入仓库 `Releases → v1.0.1 → Assets` 下载六个平台的文件。

## 3. 本地使用

Native Image（Windows x64、Linux x64/arm64、macOS ARM64）：

```bash
# 仅输出一行机器码（Windows 可加 .exe）
./HzTechMachineCode --cli

# 查看使用的 UUID/序列号来源（不会输出原始序列号）
./HzTechMachineCode --info

# 显示系统本机窗口（Windows PowerShell/macOS osascript/Linux zenity 可用时）
./HzTechMachineCode --show

# 不读取硬件，验证编译出的程序是否具备正确的哈希实现
./HzTechMachineCode --self-test

./HzTechMachineCode --help
```

Windows：解压 ZIP，直接双击 `HzTechMachineCode.exe` 会尝试弹出系统窗口。Linux 没安装 zenity 时，`--show` 自动退化为终端输出；macOS 建议从终端启动，原生二进制不是 `.app` 包。未进行代码签名/苹果公证，可能触发 Windows SmartScreen 或 macOS Gatekeeper 提示。

老版本 Java Swing GUI 和 V1 兼容（仍可独立编译 JAR）：

```bash
mvn clean verify
java -jar target/hztech-machine-code.jar
java -jar target/hztech-machine-code.jar --cli
java -jar target/hztech-machine-code.jar --legacy
```

## 4. 构建 GraalVM Native Image

要求：安装 **GraalVM Community JDK 21**（含 `native-image`）、Maven 3.9+、对应系统的原生 C 编译工具链。Windows 需要 Visual Studio C++ Build Tools，macOS 需要 Xcode Command Line Tools，Linux 需要 GCC/glibc 开发包。

```bash
mvn -B -ntp -DskipTests -Pnative clean package
# Linux/macOS 输出：target/HzTechMachineCode
# Windows 输出：target/HzTechMachineCode.exe
```

单文件 Native Image **并非完全静态链接**：Linux 仍可能依赖目标系统的 glibc 及其他基础库。应在目标发行版实机测试。默认编译的原生程序是 CLI/系统对话框模式，不是 Swing 的同款图形界面。

## 5. OSHI 与 Native 硬件来源的差别（务必阅读）

Java JAR 使用 OSHI/JNA（你的原实现）；为了使 Native Image 不依赖 JNI、复杂反射及 Swing，原生版改用操作系统标准接口读取硬件标识：

| 系统 | Native 读取来源 |
| --- | --- |
| Windows | PowerShell CIM：`Win32_ComputerSystemProduct.UUID/IdentifyingNumber`、`Win32_BIOS.SerialNumber`、`Win32_BaseBoard.SerialNumber` |
| Linux | `/sys/devices/virtual/dmi/id/product_uuid`、`product_serial`、`board_serial`（或 `/sys/class/dmi/id/`） |
| macOS | `ioreg -rd1 -c IOPlatformExpertDevice` 的 `IOPlatformUUID` 与 `IOPlatformSerialNumber` |

读取完成后，**双方调用完全相同的 `MachineKeyGenerator.generate`**：UUID 去掉连字符并大写，校验已知无效值；UUID 不可用时回退到整机/主板序列号；最后使用相同的字段名称、前缀和 SHA-256 哈希。

但读取路径变了，**不能保证所有设备上 OSHI 与 Native 读取结果都完全相同**：尤其是虚拟机、容器、权限受限 Linux 或主板序列号回退场景。首次发放 License 之前，务必在相同设备上比较：

```bash
java -jar target/hztech-machine-code.jar --cli
./target/HzTechMachineCode --cli
```

结果应完全一致。如果不同，不要把新 Native 版签发的机器码与旧校验端混用，需要先确认原始硬件来源和兼容策略；`--info` 能帮助排查 Native 的来源。

**不能仅凭机器码防破解**。正式 License 请由签发端私钥签名，客户端只保留公钥；勿上传任何私钥、客户数据、付费密钥到 GitHub。Native 可执行文件依然可能被逆向分析。

## 6. 项目结构

```text
.github/workflows/build.yml               # 按 v* Tag 构建四 Native + 两兼容包并发布
pom.xml                                   # JVM 构建 + -Pnative 编译配置
src/main/java/org/hztech/core/plugin/license/common/util/
  MachineKeyGenerator.java                # V2 唯一算法（JVM + Native 共用）
  GetMachineUtil.java                     # OSHI 硬件读取，旧 JAR/V1 兼容
  MachineCodeApp.java                     # 旧版 Swing GUI / CLI
  NativeHardwareReader.java               # 原生版硬件读取（不依赖 OSHI/JNA）
  NativeMachineCodeApp.java               # 原生入口（CLI / 系统本机对话框）
src/test/java/org/hztech/core/plugin/license/common/util/
  MachineKeyGeneratorTest.java
  NativeHardwareReaderTest.java
```

> 状态：提供 GitHub Actions 构建配置与源码。必须以实际 CI 和目标操作系统运行结果为准，不保证六个平台在未经运行验证前全部构建成功。

param(
    [switch]$Connected
)

$ErrorActionPreference = "Stop"

function Invoke-Step {
    param(
        [string]$Title,
        [scriptblock]$Action
    )

    Write-Host ""
    Write-Host "==> $Title" -ForegroundColor Cyan
    & $Action
    if ($LASTEXITCODE -ne 0) {
        throw "步骤失败：$Title"
    }
}

# PowerShell 校验脚本把本地测试入口收敛成固定顺序，
# 这样开发者不需要每次回忆“当前该先跑什么、何时再跑设备测试”。
Invoke-Step "运行主机单测" { .\gradlew.bat testDebugUnitTest }
Invoke-Step "构建 AndroidTest APK" { .\gradlew.bat assembleDebugAndroidTest }

if ($Connected) {
    Invoke-Step "运行设备 / 模拟器 AndroidTest" { .\gradlew.bat connectedDebugAndroidTest }
} else {
    Write-Host ""
    Write-Host "跳过 connectedDebugAndroidTest；如需设备验证，请使用 -Connected。" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "测试校验完成。" -ForegroundColor Green

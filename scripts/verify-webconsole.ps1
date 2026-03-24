param(
    [switch]$SkipAssemble
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

# 网页后台校验脚本固定资源生成、语法检查和网页侧回归顺序，
# 是为了让富后台后续迭代不再依赖人工记忆一串零散命令。
Invoke-Step "校验网页后台入口产物与体量预算" {
    node .\scripts\build-webconsole.mjs --check
}

Invoke-Step "检查网页后台模块语法" {
    Get-ChildItem -Path .\app\src\main\assets\webconsole -Recurse -File -Include *.js |
        ForEach-Object {
            node --check $_.FullName
            if ($LASTEXITCODE -ne 0) {
                throw "语法检查失败：$($_.FullName)"
            }
        }
}

Invoke-Step "运行网页后台相关主机单测" {
    .\gradlew.bat testDebugUnitTest --tests "com.kariscode.yike.data.webconsole.*"
}

if (-not $SkipAssemble) {
    Invoke-Step "构建 Debug APK" {
        .\gradlew.bat assembleDebug
    }
} else {
    Write-Host ""
    Write-Host "跳过 assembleDebug；如需完整资源打包回归，请不要传 -SkipAssemble。" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "网页后台校验完成。" -ForegroundColor Green

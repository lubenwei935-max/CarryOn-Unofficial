# P1 · 方案 A：删除游戏 jar 的签名条目（只动沙箱实例）
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File test\desig-sandbox.ps1                     # 去签名
#   powershell -ExecutionPolicy Bypass -File test\desig-sandbox.ps1 -Check              # 只看现状
#   powershell -ExecutionPolicy Bypass -File test\desig-sandbox.ps1 -Revert             # 回滚
#   powershell -ExecutionPolicy Bypass -File test\desig-sandbox.ps1 -LogPath out.txt    # 同时落盘
#
# 安全约束：脚本只允许在 E:\WM-test 沙箱上执行。
#
# 为什么删条目交给 desig-jar.py：
#   .NET 的 ZipArchive Update 模式会顺手重写 0 字节的目录条目（csize 0 -> 2），
#   unzip -t 会报 bad CRC。desig-jar.py 只改中央目录，本地数据区一个字节都不动。

param(
    [string]$Mc = "E:\WM-test\.minecraft",
    [switch]$Check,
    [switch]$Revert,
    [string]$LogPath = ""
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
$env:PYTHONIOENCODING = "utf-8"

if ($Mc -like "*Weapon And Magic*" -and $Mc -notlike "*WM-test*") {
    throw "拒绝操作真实实例：$Mc（只允许 E:\WM-test 沙箱）"
}

$lines = New-Object System.Collections.Generic.List[string]
function Log([string]$text) { $lines.Add($text) }

# python：跳过 WindowsApps 下的"应用执行别名"（非交互进程调用会直接 9009）
$python = $null
$pyPrefix = @()
foreach ($cand in @("py", "python3", "python")) {
    $cmd = Get-Command $cand -ErrorAction SilentlyContinue
    if ($null -ne $cmd -and $cmd.Source -notlike "*WindowsApps*") {
        $python = $cmd.Source
        if ($cand -eq "py") { $pyPrefix = @("-3") }
        break
    }
}
if ($null -eq $python) { throw "需要 python 3（desig-jar.py 用）" }
$desigTool = Join-Path $PSScriptRoot "desig-jar.py"

$forgeDir = Join-Path $Mc "libraries\net\minecraftforge\forge\1.7.10-10.13.4.1614-1.7.10"

$targets = @(
    @{
        Jar     = Join-Path $Mc "versions\1.7.10-Forge_10.13.4.1614\1.7.10-Forge_10.13.4.1614.jar"
        Entries = @("META-INF/MOJANGCS.SF", "META-INF/MOJANGCS.RSA")
        Signer  = "Mojang"
    },
    # 实际被加载的是这个：崩溃日志的 MOD 列表里写的就是 forge-1.7.10-10.13.4.1614-1.7.10.jar。
    # 带 -universal 后缀的是安装器残留（版本 json 里没有 downloads.artifact，不会进 classpath）。
    @{
        Jar     = Join-Path $forgeDir "forge-1.7.10-10.13.4.1614-1.7.10.jar"
        Entries = @("META-INF/FORGE.SF", "META-INF/FORGE.DSA")
        Signer  = "Forge(实际加载)"
    },
    @{
        Jar     = Join-Path $forgeDir "forge-1.7.10-10.13.4.1614-1.7.10-universal.jar"
        Entries = @("META-INF/FORGE.SF", "META-INF/FORGE.DSA")
        Signer  = "Forge(universal)"
    }
)

function Get-SigEntries([string]$path) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($path)
    try {
        $found = @($zip.Entries | Where-Object {
            $_.FullName -match '^META-INF/.*\.(SF|RSA|DSA|EC)$'
        } | ForEach-Object { $_.FullName })
    } finally {
        $zip.Dispose()
    }
    return $found
}

function Get-EntryCount([string]$path) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($path)
    try { return $zip.Entries.Count } finally { $zip.Dispose() }
}

foreach ($t in $targets) {
    $jar = $t.Jar
    $orig = "$jar.orig"

    if (-not (Test-Path -LiteralPath $jar)) {
        Log "[跳过] 找不到 $jar"
        continue
    }

    Log ""
    Log "== $($t.Signer) : $jar"

    if ($Revert) {
        if (Test-Path -LiteralPath $orig) {
            Copy-Item -LiteralPath $orig -Destination $jar -Force
            $sig = (Get-SigEntries $jar) -join ", "
            Log "   已用 .orig 覆盖回去，剩余签名条目：$sig"
        } else {
            Log "   没有 .orig 备份，无法回滚"
        }
        continue
    }

    $before = @(Get-SigEntries $jar)
    Log ("   当前签名条目：" + $(if ($before.Count) { $before -join ", " } else { "<无>" }))

    if ($Check) { continue }

    if ($before.Count -eq 0) {
        Log "   已经是无签名状态，跳过"
        continue
    }

    if (-not (Test-Path -LiteralPath $orig)) {
        Copy-Item -LiteralPath $jar -Destination $orig -Force
        Log "   已备份 -> $orig"
    } else {
        Log "   备份已存在，保留原始 .orig 不覆盖"
    }

    $countBefore = Get-EntryCount $jar
    foreach ($name in $t.Entries) { Log "   删除 $name" }

    $pyOut = & $python @pyPrefix $desigTool --quiet $jar @($t.Entries) 2>&1
    if ($LASTEXITCODE -ne 0) {
        if ($pyOut) { foreach ($o in $pyOut) { Log "   [python] $o" } }
        throw "desig-jar.py 执行失败（退出码 $LASTEXITCODE）"
    }

    $countAfter = Get-EntryCount $jar
    $after = @(Get-SigEntries $jar)
    $leftText = if ($after.Count) { $after -join ", " } else { "<无>" }
    Log ("   条目数 {0} -> {1}；剩余签名条目：{2}" -f $countBefore, $countAfter, $leftText)

    if ($after.Count -ne 0) { throw "仍有签名条目残留：$($after -join ', ')" }
}

Log ""
Log "== 汇总"
foreach ($t in $targets) {
    if (Test-Path -LiteralPath $t.Jar) {
        $sig = @(Get-SigEntries $t.Jar)
        $state = if ($sig.Count) { "已签名（$($sig -join ', ')）" } else { "未签名" }
        Log ("   {0,-16}{1}" -f $t.Signer, $state)
    }
}

$report = $lines -join "`r`n"
Write-Output $report
if ($LogPath) { Set-Content -LiteralPath $LogPath -Value $report -Encoding UTF8 }

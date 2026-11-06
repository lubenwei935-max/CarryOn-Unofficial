# BackportAPI 包冲突护栏（PowerShell 版，逻辑同 check-packages.sh）
#
# 检查产物里是否有类落进了「1.7.10 游戏 jar 里已存在、且已签名」的包 ——
# 这类类会在 preInit 抛 SecurityException: signer information does not match。
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File check-packages.ps1
#   powershell -ExecutionPolicy Bypass -File check-packages.ps1 -Jar path\to\x.jar
#
# 退出码：0 = 无新增冲突；1 = 出现白名单外的冲突类。

param(
    [string]$Jar = ""
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$root = $PSScriptRoot
$bp = Join-Path $root "backportapi"
$deps = Join-Path $bp "deps"
$whitelist = Join-Path $bp "conf\known-conflicts.txt"

if (-not $Jar) {
    $cand = Get-ChildItem -Path (Join-Path $bp "build") -Filter "backportapi-*.jar" -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($cand) { $Jar = $cand.FullName }
}

$lines = New-Object System.Collections.Generic.List[string]
function Log([string]$text) { $lines.Add($text) }

Log "== BackportAPI 包冲突护栏 =="
Log "产物     : $(if ($Jar) { $Jar } else { '<未找到>' })"
Log "对照     : deps/forgebin.jar + deps/mcp.jar"
Log "白名单   : backportapi/conf/known-conflicts.txt"
Log ""

function Get-Classes([string]$path) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($path)
    try {
        return @($zip.Entries |
                 Where-Object { $_.FullName -like "*.class" } |
                 ForEach-Object { $_.FullName.Substring(0, $_.FullName.Length - 6).Replace("/", ".") })
    } finally { $zip.Dispose() }
}

function Get-Package([string]$class) {
    $i = $class.LastIndexOf(".")
    if ($i -lt 0) { return "" }
    return $class.Substring(0, $i)
}

function Get-Signer([string]$package) {
    if ($package -like "net.minecraftforge.*") { return "Forge" }
    if ($package -like "net.minecraft.*") { return "Mojang" }
    return "未知"
}

if (-not $Jar -or -not (Test-Path -LiteralPath $Jar)) {
    Log "[跳过] 找不到产物 jar —— 先跑 backportapi\build.ps1"
    Write-Output ($lines -join "`r`n"); exit 0
}
foreach ($dep in @("forgebin.jar", "mcp.jar")) {
    if (-not (Test-Path -LiteralPath (Join-Path $deps $dep))) {
        Log "[跳过] 缺少对照用的 dev 产物：deps\$dep"
        Write-Output ($lines -join "`r`n"); exit 0
    }
}
if (-not (Test-Path -LiteralPath $whitelist)) {
    Log "[跳过] 缺少白名单：backportapi\conf\known-conflicts.txt"
    Write-Output ($lines -join "`r`n"); exit 0
}

$ours = Get-Classes $Jar
$game = @(Get-Classes (Join-Path $deps "forgebin.jar")) + @(Get-Classes (Join-Path $deps "mcp.jar"))

$gamePkgs = New-Object System.Collections.Generic.HashSet[string]
foreach ($c in $game) { [void]$gamePkgs.Add((Get-Package $c)) }

# 按「包」求交集：冲突是包级别的签名一致性，不是类名重复
$conflictPkgs = New-Object System.Collections.Generic.HashSet[string]
foreach ($c in $ours) {
    $p = Get-Package $c
    if ($gamePkgs.Contains($p)) { [void]$conflictPkgs.Add($p) }
}

$conflictClasses = @($ours | Where-Object { $conflictPkgs.Contains((Get-Package $_)) } | Sort-Object -Unique)

Log "冲突包 $($conflictPkgs.Count) 个，落进去的类 $($conflictClasses.Count) 个："
foreach ($p in ($conflictPkgs | Sort-Object)) {
    $n = @($conflictClasses | Where-Object { (Get-Package $_) -eq $p }).Count
    Log ("  {0,-45} {1,-7} {2} 个" -f $p, (Get-Signer $p), $n)
}
Log ""

$known = @{}
foreach ($line in Get-Content -LiteralPath $whitelist -Encoding UTF8) {
    if ($line -match "^\s*#") { continue }
    $parts = $line -split "\|"
    if ($parts.Count -lt 2) { continue }
    $cls = $parts[1].Trim()
    if ($cls) { $known[$cls] = $parts[0].Trim() }
}

$new = @($conflictClasses | Where-Object { -not $known.ContainsKey($_) })
$gone = @($known.Keys | Where-Object { $conflictClasses -notcontains $_ })

if ($new.Count -gt 0) {
    Log "!! 新增冲突类（不在白名单里）："
    foreach ($cls in $new) {
        $p = Get-Package $cls
        Log ("   {0,-7} {1}   （包 {2}）" -f (Get-Signer $p), $cls, $p)
    }
    Log ""
    Log "   这些类会让游戏在 preInit 阶段抛 SecurityException（同包签名不一致）。"
    Log "   两条出路："
    Log "     1) 换包名（如 net.backportapi.shim.xxx），调用方改 import；"
    Log "     2) 确认无解后，把它登记到 backportapi/conf/known-conflicts.txt，并说明为什么必须沿用原包名。"
    Log ""
    Log "结果：FAIL"
    Write-Output ($lines -join "`r`n")
    exit 1
}

if ($gone.Count -gt 0) {
    Log "提示：白名单里有 $($gone.Count) 个类已不在产物中，可以把它们从 known-conflicts.txt 删掉（可收紧）："
    foreach ($cls in $gone) { Log "   $cls" }
    Log ""
}

Log "结果：PASS（$($conflictClasses.Count) 个冲突类全部在白名单内，无新增）"
Write-Output ($lines -join "`r`n")
exit 0

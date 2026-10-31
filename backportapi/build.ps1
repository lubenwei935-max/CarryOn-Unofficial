# BackportAPI Lib - ForgeGradle-free build pipeline
#   1. javac (MCP names)                                    -> build/classes
#   2. jar                                                  -> build/classes-mcp.jar
#   3. SpecialSource reobf (mcp2srg + inheritance)          -> build/classes-srg.jar
#   4. package                                              -> build/backportapi-<version>.jar
#
# NOTE: all native calls go through `cmd /c` with explicit quotes, because
# PowerShell 5.1 does not quote arguments containing spaces when invoking
# native executables (and this project path contains "WorkBuddy AI").
param(
    [string]$Version = "1.0.0",
    [switch]$SkipReobf
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$build = Join-Path $root "build"
$deps = Join-Path $root "deps"
$conf = Join-Path $root "conf"
$srcRoot = Join-Path $root "src\main\java"
$resRoot = Join-Path $root "src\main\resources"

$javac = Join-Path $env:JAVA_HOME "bin\javac.exe"
if (-not (Test-Path $javac)) { $javac = "javac" }
$jarExe = Join-Path $env:JAVA_HOME "bin\jar.exe"
if (-not (Test-Path $jarExe)) { $jarExe = "jar" }

# forgebin.jar MUST come first: it is the only artifact that still carries
# InnerClasses attributes, which javac needs to resolve nested types such as
# ClickEvent.Action or PlayerInteractEvent.RightClickBlock.
$cp = @(
    (Join-Path $deps "forgebin.jar"),
    (Join-Path $deps "mcp.jar"),
    (Join-Path $deps "forge-mcp.jar"),
    (Join-Path $deps "lwjgl-2.9.3.jar"),
    (Join-Path $deps "netty-all-4.0.23.Final.jar"),
    (Join-Path $deps "guava-17.0.jar"),
    (Join-Path $deps "authlib-1.5.21.jar")
) | ForEach-Object { (Resolve-Path -LiteralPath $_).Path }
$cpStr = [string]::Join(";", $cp)

function Invoke-Cmd($label, $commandLine) {
    Write-Host "==> $label" -ForegroundColor Cyan
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & cmd /c $commandLine 2>&1 | ForEach-Object { Write-Host "    $_" }
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($null -ne $code -and $code -ne 0) {
        throw "$label failed with exit code $code"
    }
}

$env:JAVA_TOOL_OPTIONS = $null

# --- 1. compile ---
$classes = Join-Path $build "classes"
if (Test-Path $classes) { Remove-Item -Recurse -Force $classes }
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources = Get-ChildItem -Path $srcRoot -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$srcList = Join-Path $build "sources.txt"
$sources | Set-Content -Path $srcList -Encoding ASCII

Invoke-Cmd "javac ($($sources.Count) files)" `
    "`"$javac`" -encoding UTF-8 -source 8 -target 8 -nowarn -proc:none -cp `"$cpStr`" -d `"$classes`" @`"$srcList`""

# --- 2. jar the MCP-named classes ---
$mcpJar = Join-Path $build "classes-mcp.jar"
if (Test-Path $mcpJar) { Remove-Item -Force $mcpJar }
Invoke-Cmd "jar classes-mcp.jar" "cd /d `"$classes`" && `"$jarExe`" cf `"$mcpJar`" ."

if ($SkipReobf) {
    Write-Host "skip reobf requested" -ForegroundColor Yellow
    return
}

# --- 3. reobf MCP -> SRG ---
$srgJar = Join-Path $build "classes-srg.jar"
if (Test-Path $srgJar) { Remove-Item -Force $srgJar }
$ss = Join-Path $deps "SpecialSource-1.11.4-shaded.jar"
Invoke-Cmd "reobf mcp2srg" `
    "`"$env:JAVA_HOME\bin\java.exe`" -jar `"$ss`" --in-jar `"$mcpJar`" --out-jar `"$srgJar`" --srg-in `"$(Join-Path $conf 'mcp2srg.srg')`" --read-inheritance `"$(Join-Path $conf 'inheritance.map')`" -q"

# --- 4. package ---
$stage = Join-Path $build "pkg"
if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
New-Item -ItemType Directory -Force -Path $stage | Out-Null
Invoke-Cmd "unpack srg classes" "cd /d `"$stage`" && `"$jarExe`" xf `"$srgJar`""
Copy-Item -Force (Join-Path $resRoot "mcmod.info") $stage

$out = Join-Path $build "backportapi-$Version.jar"
if (Test-Path $out) { Remove-Item -Force $out }
Invoke-Cmd "jar $Version" "cd /d `"$stage`" && `"$jarExe`" cf `"$out`" mcmod.info ."

Write-Host "built: $out ($((Get-Item $out).Length) bytes)" -ForegroundColor Green

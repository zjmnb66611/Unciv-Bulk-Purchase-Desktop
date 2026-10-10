param(
    [string]$GameDirectory = (Join-Path $PSScriptRoot '..\..'),
    [switch]$ValidateOnly
)
$ErrorActionPreference = 'Stop'
$gameRoot = (Resolve-Path -LiteralPath $GameDirectory).Path
$javaPath = Join-Path $gameRoot 'jre\bin\javaw.exe'
$jarPath = Join-Path $gameRoot 'Unciv.jar'
$agentPath = Join-Path $PSScriptRoot 'bulk-purchase-agent.jar'
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if (-not (Test-Path -LiteralPath $javaPath)) { throw '找不到游戏自带的Java运行环境。' }
if ((Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.gameSha256) {
    throw '此补丁只适配已验证的Unciv 4.22.6-patch1，请勿强行加载其他版本。'
}
if ((Get-FileHash -LiteralPath $agentPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.agentSha256) {
    throw '批量购买补丁文件校验失败。'
}
if ($ValidateOnly) { Write-Output '已验证游戏版本、Java和补丁哈希；没有启动游戏。'; return }
$running = Get-CimInstance Win32_Process -Filter "Name='Unciv.exe' OR Name='java.exe' OR Name='javaw.exe'" |
    Where-Object { $_.ExecutablePath -eq (Join-Path $gameRoot 'Unciv.exe') -or
        ($_.ExecutablePath -eq $javaPath -and $_.CommandLine -match 'Unciv\.jar') }
if ($running) { throw '请先保存并退出正在运行的Unciv，再使用此启动器。' }
$launchArgs = @('-Xmx4G','-Dunciv.packr=true',('-javaagent:"' + $agentPath + '"'),'-jar',('"' + $jarPath + '"'))
Start-Process -FilePath $javaPath -ArgumentList $launchArgs -WorkingDirectory $gameRoot -WindowStyle Hidden

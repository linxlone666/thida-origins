param([string[]]$Tasks = @('build', 'originDatapack'))
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not $env:JAVA_HOME) {
    $thidaJdk = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '.tooling/java') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($thidaJdk) { $env:JAVA_HOME = $thidaJdk.FullName }
}
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-cache'
& (Join-Path $PSScriptRoot 'gradlew.bat') --no-daemon @Tasks
exit $LASTEXITCODE

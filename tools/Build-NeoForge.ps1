param([switch]$GameTests, [switch]$WithoutIron, [string]$FakePlayerProfile, [string]$CompatProfile, [switch]$AllProfileMods)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$bundledJdks = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tooling/java') -Directory -ErrorAction SilentlyContinue
$jdk = $bundledJdks | Where-Object { $_.Name -like 'jdk-21*' -and (Test-Path -LiteralPath (Join-Path $_.FullName 'bin/javac.exe')) } | Select-Object -First 1
if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
    throw 'Install a Java 21 JDK and set JAVA_HOME, or put it in .tooling/java/jdk-21...'
}
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-cache'
$env:GRADLE_OPTS = '-Dorg.gradle.jvmargs=-Xmx1g'
$gradleArguments = @('test', 'jar', 'originDatapack', '--console=plain')
if ($GameTests) { $gradleArguments += 'runGameTestServer' }
if ($WithoutIron) { $gradleArguments += '-PwithoutIron=true' }
if ($FakePlayerProfile) { $gradleArguments += "-PfakePlayerProfile=$FakePlayerProfile" }
if ($CompatProfile) { $gradleArguments += "-PcompatProfile=$CompatProfile" }
if ($AllProfileMods) {
    if (!$CompatProfile) { throw '-AllProfileMods requires -CompatProfile' }
    $gradleArguments += '-PallProfileMods','-PwithConnector'
}
Push-Location -LiteralPath $projectRoot
try {
    & (Join-Path $projectRoot 'gradlew.bat') @gradleArguments
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }

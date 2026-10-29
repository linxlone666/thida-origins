param([switch]$GameTests, [switch]$Offline, [string]$FakePlayerProfile, [string]$CompatProfile, [switch]$AllProfileMods)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$bundledJdks = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tooling/java') -Directory -ErrorAction SilentlyContinue
$jdk = $bundledJdks | Where-Object { $_.Name -like 'jdk-21*' -and (Test-Path -LiteralPath (Join-Path $_.FullName 'bin/javac.exe')) } | Select-Object -First 1
if (!$env:JAVA_HOME -and $jdk) { $env:JAVA_HOME = $jdk.FullName }
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
    throw 'Install a Java 21 JDK and set JAVA_HOME, or put it in .tooling/java/jdk-21...'
}
if (!$env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-cache' }
$gradleArguments = @('build', 'originDatapack', '--console=plain')
if ($GameTests) { $gradleArguments += 'runGameTestServer' }
if ($Offline) { $gradleArguments += '--offline' }
if ($FakePlayerProfile) { $gradleArguments += "-PfakePlayerProfile=$FakePlayerProfile" }
if ($CompatProfile) { $gradleArguments += "-PcompatProfile=$CompatProfile" }
if ($AllProfileMods) {
    if (!$CompatProfile) { throw '-AllProfileMods requires -CompatProfile' }
    $gradleArguments += '-PallProfileMods','-PwithConnector'
}
foreach ($profilePath in @($FakePlayerProfile, $CompatProfile)) {
    if ($profilePath -and !(Test-Path -LiteralPath (Join-Path $profilePath 'mods') -PathType Container)) {
        throw "Profile mods directory not found: $profilePath"
    }
}
Push-Location -LiteralPath $projectRoot
try {
    # Direct Java invocation also supports JAVA_HOME paths containing batch metacharacters such as &.
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -classpath (Join-Path $projectRoot 'gradle/wrapper/gradle-wrapper.jar') org.gradle.wrapper.GradleWrapperMain @gradleArguments
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }

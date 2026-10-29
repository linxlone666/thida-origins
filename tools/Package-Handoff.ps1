param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$versionLine = Get-Content -LiteralPath (Join-Path $projectRoot 'gradle.properties') | Where-Object { $_ -match '^mod_version=' }
$version = ($versionLine -split '=',2)[1].Trim()
$destination = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $destination) { throw 'Choose a new output directory; existing handoffs are preserved.' }
$jarName = "thida-origins-1.21.1-neoforge-$version.jar"
$jar = Join-Path $projectRoot "build-neoforge/libs/$jarName"
if (!(Test-Path -LiteralPath $jar)) { throw 'Build the current version first.' }
$sourceFiles = @('build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','.gitignore','README.md','LICENSE')
foreach ($name in @('src','gradle','docs','tools') + $sourceFiles) {
    $inputPath = Join-Path $projectRoot $name
    if (!(Test-Path -LiteralPath $inputPath)) { throw "Missing handoff input: $inputPath" }
}
# Copying into one of the source directories would recursively include the output itself.
foreach ($name in @('src','gradle','docs','tools','archive','.git')) {
    $protectedPath = [IO.Path]::GetFullPath((Join-Path $projectRoot $name)).TrimEnd([IO.Path]::DirectorySeparatorChar)
    if ($destination.Equals($protectedPath, [StringComparison]::OrdinalIgnoreCase) -or
        $destination.StartsWith($protectedPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Choose an output directory outside $name."
    }
}
New-Item -ItemType Directory -Path $destination | Out-Null
$source = Join-Path $destination 'source'
New-Item -ItemType Directory -Path $source | Out-Null
foreach ($name in @('src','gradle')) { Copy-Item -LiteralPath (Join-Path $projectRoot $name) -Destination $source -Recurse }
foreach ($name in $sourceFiles) {
    Copy-Item -LiteralPath (Join-Path $projectRoot $name) -Destination $source
}
$tools = Join-Path $source 'tools'
New-Item -ItemType Directory -Path $tools | Out-Null
foreach ($name in @('Build-NeoForge.ps1','Package-Handoff.ps1')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $tools }
$docs = Join-Path $source 'docs'
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs') -Destination $docs -Recurse
Copy-Item -LiteralPath $jar -Destination $destination
Compress-Archive -LiteralPath (Get-ChildItem -LiteralPath $source -Force).FullName -DestinationPath (Join-Path $destination "thida-source-$version.zip")
$hashes = Get-ChildItem -LiteralPath $destination -File | Where-Object { $_.Extension -in @('.jar','.zip') } | Sort-Object Name | ForEach-Object {
    (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() + '  ' + $_.Name
}
[IO.File]::WriteAllLines((Join-Path $destination 'SHA256SUMS.txt'),$hashes,[Text.UTF8Encoding]::new($false))
Get-ChildItem -LiteralPath $destination | Select-Object Name,Length

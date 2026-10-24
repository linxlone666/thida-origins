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
New-Item -ItemType Directory -Path $destination | Out-Null
$source = Join-Path $destination 'source'
New-Item -ItemType Directory -Path $source | Out-Null
foreach ($name in @('src','gradle')) { Copy-Item -LiteralPath (Join-Path $projectRoot $name) -Destination $source -Recurse }
foreach ($name in @('build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','.gitignore','README.md','LICENSE')) {
    Copy-Item -LiteralPath (Join-Path $projectRoot $name) -Destination $source
}
$tools = Join-Path $source 'tools'
New-Item -ItemType Directory -Path $tools | Out-Null
foreach ($name in @('Build-NeoForge.ps1','Package-Handoff.ps1')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $tools }
$docs = Join-Path $source 'docs'
New-Item -ItemType Directory -Path $docs | Out-Null
foreach ($name in @('ARCHITECTURE_TH.md','PERSONAL_MOD_INTEGRATION_TH.md','SKILL_EFFECT_ADAPTER_TH.md','SKILLS_V7_TH.md','DOLL_INVENTORY_0_7_1_TH.md','CHANGELOG_0_8_1_TH.md','HUD_0_8_2_TH.md','HUD_0_8_3_TH.md','FONT_0_8_4_TH.md')) {
    Copy-Item -LiteralPath (Join-Path $projectRoot "docs/$name") -Destination $docs
}
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs/examples') -Destination $docs -Recurse
Copy-Item -LiteralPath $jar -Destination $destination
Copy-Item -LiteralPath (Join-Path $docs 'FONT_0_8_4_TH.md') -Destination $destination
Compress-Archive -LiteralPath (Get-ChildItem -LiteralPath $source -Force).FullName -DestinationPath (Join-Path $destination "thida-source-$version.zip")
$hashes = Get-ChildItem -LiteralPath $destination -File | Where-Object { $_.Extension -in @('.jar','.zip') } | ForEach-Object {
    (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() + '  ' + $_.Name
}
[IO.File]::WriteAllLines((Join-Path $destination 'SHA256SUMS.txt'),$hashes,[Text.UTF8Encoding]::new($false))
Get-ChildItem -LiteralPath $destination | Select-Object Name,Length

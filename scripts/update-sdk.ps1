# Rebuilds the bundled SDK copy in sdk-repo\ from a checkout of tether-xmpp.
#   powershell -ExecutionPolicy Bypass -File scripts\update-sdk.ps1 [path-to-tether-xmpp]
# Only the JVM artifacts are built, so no Kotlin/Native toolchain is needed.
param([string]$Sdk = "")
$ErrorActionPreference = "Stop"
$Demo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ($Sdk -eq "") { $Sdk = Join-Path $Demo "..\tether-xmpp" }
$Sdk = (Resolve-Path $Sdk).Path
$Modules = "tether-xml","tether-debug","tether-crypto","tether-sasl","tether-transport","tether-core","tether-im"
$Tasks = @()
foreach ($m in $Modules) {
  $Tasks += ":${m}:publishJvmPublicationToMavenLocal"
  $Tasks += ":${m}:publishKotlinMultiplatformPublicationToMavenLocal"
}
$Repo = Join-Path $Demo "sdk-repo"
if (Test-Path $Repo) { Remove-Item -Recurse -Force $Repo }
New-Item -ItemType Directory -Path $Repo | Out-Null
Push-Location $Sdk
try {
  & .\gradlew.bat -q "-Ptether.android=false" "-Dmaven.repo.local=$Repo" @Tasks
  if ($LASTEXITCODE -ne 0) { throw "SDK build failed" }
} finally { Pop-Location }
Get-ChildItem -Path $Repo -Recurse -Filter "maven-metadata-local.xml" | Remove-Item -Force
$Commit = (git -C $Sdk rev-parse HEAD 2>$null)
if (-not $Commit) { $Commit = "unknown" }
"tether-xmpp $Commit" | Set-Content -Encoding ascii (Join-Path $Repo "SDK_VERSION")
Write-Host "sdk-repo updated from $Sdk at $Commit"

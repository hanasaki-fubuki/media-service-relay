param(
    [string]$AndroidTools = $env:ANDROID_HOME
)

$ErrorActionPreference = 'Stop'

function Invoke-BuildTool {
    param([string]$Executable, [string[]]$Arguments)
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Executable exited with code $LASTEXITCODE."
    }
}

if (!$AndroidTools) { $AndroidTools = $env:ANDROID_SDK_ROOT }
if (!$AndroidTools) {
    $properties = Join-Path $PSScriptRoot 'local.properties'
    if (Test-Path -LiteralPath $properties) {
        $sdkLine = Select-String -LiteralPath $properties -Pattern '^\s*sdk\.dir\s*=\s*(.+)$' | Select-Object -First 1
        if ($sdkLine) {
            $AndroidTools = $sdkLine.Matches[0].Groups[1].Value.Replace('\:', ':').Replace('\\', '\')
        }
    }
}
if (!$AndroidTools) { throw 'Set ANDROID_HOME, set sdk.dir in local.properties, or pass -AndroidTools.' }
$env:ANDROID_HOME = (Resolve-Path -LiteralPath $AndroidTools).Path
$buildTools = Join-Path $env:ANDROID_HOME 'build-tools/36.0.0'
$gradle = Join-Path $PSScriptRoot 'gradlew.bat'
$build = Join-Path $PSScriptRoot 'build'
$output = Join-Path $build 'Media-Service-Relay.apk'

Invoke-BuildTool $gradle @('-p', $PSScriptRoot, '--console=plain', 'assembleRelease')
$releases = Join-Path $build 'outputs/apk/release'
$unsigned = (Get-ChildItem -LiteralPath $releases -Filter '*-release-unsigned.apk' | Select-Object -First 1).FullName
if (!$unsigned) { throw 'Gradle did not produce an unsigned release APK.' }

# This local test key is used only to sign the sideloadable prototype.
$key = Join-Path $build 'relay-test.p12'
if (-not (Test-Path -LiteralPath $key)) {
    Invoke-BuildTool 'keytool' @(
        '-genkeypair', '-keystore', $key, '-storetype', 'PKCS12',
        '-storepass', 'android', '-alias', 'qq-music-relay-test', '-keyalg', 'RSA',
        '-keysize', '2048', '-validity', '3650', '-dname', 'CN=QQ Music Relay Local Test'
    )
}
Invoke-BuildTool (Join-Path $buildTools 'apksigner.bat') @(
    'sign', '--ks', $key, '--ks-pass', 'pass:android', '--out', $output, $unsigned
)
Invoke-BuildTool (Join-Path $buildTools 'apksigner.bat') @('verify', '--verbose', $output)
Get-Item -LiteralPath $output

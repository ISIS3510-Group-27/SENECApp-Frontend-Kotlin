$ErrorActionPreference = 'Stop'
$repoPath = Split-Path -Parent $PSScriptRoot
$classPath = Join-Path $repoPath 'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
$detectorPath = Join-Path $classPath 'com/senecapp/sensors/ShakeDetector.class'
if (-not (Test-Path -LiteralPath $detectorPath)) {
    throw 'Build the debug app first: ./gradlew assembleDebug --offline'
}
$jdkPath = $env:JAVA_HOME
if (-not $jdkPath) { $jdkPath = 'C:/Program Files/Android/Android Studio/jbr' }
$outputPath = Join-Path $repoPath 'app/build/shake-detector-checks'
$null = New-Item -ItemType Directory -Path $outputPath -Force
& (Join-Path $jdkPath 'bin/javac.exe') -cp $classPath -d $outputPath (Join-Path $PSScriptRoot 'ShakeDetectorChecks.java')
if ($LASTEXITCODE -ne 0) { throw 'Could not compile detector checks' }
& (Join-Path $jdkPath 'bin/java.exe') -cp "$outputPath;$classPath" com.senecapp.sensors.ShakeDetectorChecks
if ($LASTEXITCODE -ne 0) { throw 'Detector checks failed' }

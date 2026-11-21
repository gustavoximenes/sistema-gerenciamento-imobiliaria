$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
New-Item -ItemType Directory -Force build, build/lib, build/classes | Out-Null
$libs = @{
    'gson-2.13.2.jar' = 'com/google/code/gson/gson/2.13.2'
}
foreach ($jar in $libs.Keys) {
    $dest = "build/lib/$jar"
    if (!(Test-Path $dest)) {
        Invoke-WebRequest "https://repo.maven.apache.org/maven2/$($libs[$jar])/$jar" -OutFile "$dest.tmp"
        Move-Item -LiteralPath "$dest.tmp" -Destination $dest -Force
    }
}
$fontes = (Get-ChildItem src -Recurse -Filter '*.java').FullName
javac -encoding UTF-8 -cp 'build/lib/*' -d build/classes $fontes
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java -cp 'build/classes;build/lib/*' imobiliaria.App
exit $LASTEXITCODE

#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build/classes build/lib
baixar() {
    destino="build/lib/$2"
    if [ ! -f "$destino" ]; then
        curl -fLsS "https://repo.maven.apache.org/maven2/$1/$2" -o "$destino.tmp"
        mv "$destino.tmp" "$destino"
    fi
}
baixar com/h2database/h2/2.3.232 h2-2.3.232.jar
baixar com/google/code/gson/gson/2.13.2 gson-2.13.2.jar
find src -name '*.java' > build/fontes.txt
javac -encoding UTF-8 -cp 'build/lib/*' -d build/classes @build/fontes.txt
exec java -cp 'build/classes:build/lib/*' imobiliaria.App

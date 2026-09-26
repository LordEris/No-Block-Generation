#!/usr/bin/env bash
# Starts a real Fabric dedicated server with the built jar, has it generate chunks in the three
# vanilla dimensions, then checks the saved region files: no natural terrain and no fluid may be
# left in any fully generated chunk.
set -euo pipefail

JAR=$1
SCRIPTS=$(cd "$(dirname "$0")" && pwd)
MC=$(grep -E '^minecraft_version=' gradle.properties | cut -d= -f2)
LOADER=$(grep -E '^loader_version=' gradle.properties | cut -d= -f2)
INSTALLER=$(curl -fsSL https://meta.fabricmc.net/v2/versions/installer | jq -r '[.[] | select(.stable)][0].version')

rm -rf smoke-server
mkdir -p smoke-server/mods
cp "$JAR" smoke-server/mods/
cd smoke-server
echo "Fabric server: Minecraft $MC, loader $LOADER, installer $INSTALLER"
curl -fsSL -o fabric-server.jar "https://meta.fabricmc.net/v2/versions/loader/$MC/$LOADER/$INSTALLER/server/jar"
echo "eula=true" > eula.txt
cat > server.properties <<PROPS
enable-rcon=true
rcon.port=25575
rcon.password=smoke-test
broadcast-rcon-to-ops=false
level-seed=20260926
spawn-protection=0
view-distance=4
simulation-distance=4
PROPS

rcon() { python3 "$SCRIPTS/rcon.py" "$@"; }
fail() {
  echo "::error::$1"
  echo "----- server log (tail) -----"
  tail -n 200 server.log || true
  kill "$SERVER_PID" 2>/dev/null || true
  exit 1
}

java -Xmx3G -jar fabric-server.jar nogui > server.log 2>&1 &
SERVER_PID=$!

for _ in $(seq 1 180); do
  grep -q 'RCON running' server.log && break
  kill -0 "$SERVER_PID" 2>/dev/null || fail "the server stopped during startup"
  sleep 5
done
grep -q 'RCON running' server.log || fail "the server did not start within 15 minutes"

# The spawn area of the Overworld is generated at startup; force a square of chunks in the other
# two dimensions, and a second one in the Overworld away from the spawn.
rcon "execute in minecraft:the_nether run forceload add -64 -64 63 63"
rcon "execute in minecraft:the_end run forceload add -64 -64 63 63"
rcon "execute in minecraft:overworld run forceload add 512 512 639 639"
sleep 90
for dimension in minecraft:the_nether minecraft:the_end minecraft:overworld; do
  rcon "execute in $dimension run forceload remove all"
done
rcon "save-all flush"
rcon "stop" || true

for _ in $(seq 1 60); do
  kill -0 "$SERVER_PID" 2>/dev/null || break
  sleep 2
done
kill -0 "$SERVER_PID" 2>/dev/null && fail "the server did not stop"
wait "$SERVER_PID" || true

echo "----- mod log lines -----"
grep -E 'Terrain stripping|Stripping terrain|spawn platform|World spawn|matches nothing' server.log || true

for dimension in minecraft:overworld minecraft:the_nether minecraft:the_end; do
  grep -q "Stripping terrain in $dimension" server.log || fail "no chunk was stripped in $dimension"
done
grep -qE 'Placed the spawn platform|no spawn platform needed' server.log || fail "the spawn platform was neither placed nor skipped"
# Every entry of the default keptFeatures and strippedFeatures must exist in this Minecraft version.
if grep -q 'matches nothing' server.log; then
  fail "a default feature list names a feature type or id this version does not have"
fi
if grep -E 'Mixin|mixin' server.log | grep -qiE 'error|fail|exception'; then
  fail "the log reports a mixin problem"
fi
if grep -qE 'Exception|Crash report' server.log; then
  fail "the log holds an exception"
fi

python3 "$SCRIPTS/check_world.py" no-block-generation world

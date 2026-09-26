#!/usr/bin/env bash
# Dev aid: prints javap signatures for the classes listed in .github/api-dump-classes.txt
# so the mod can be written against the exact Minecraft version without local access.
set -u
JARS=$(find ~/.gradle .gradle -name '*.jar' 2>/dev/null | grep -i minecraft | grep -vi -e sources -e fabric-loader -e fabric-api | sort -u)
echo "== minecraft jars =="; echo "$JARS"
CP=$(echo "$JARS" | tr '\n' ':')
while read -r line; do
  [ -z "$line" ] && continue
  case "$line" in
    grep:*) pat="${line#grep:}"; echo "== classes matching $pat =="
      for j in $JARS; do unzip -Z1 "$j" 2>/dev/null | grep -E "$pat" | grep '\.class$' | grep -v '\$[0-9]' ; done | sort -u ;;
    *) echo "== $line =="; javap -cp "$CP" -p "$line" 2>&1 ;;
  esac
done < .github/api-dump-classes.txt

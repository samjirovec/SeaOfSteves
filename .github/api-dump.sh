#!/usr/bin/env bash
set -u
JARS=$(find ~/.gradle .gradle -name '*.jar' 2>/dev/null | grep -i minecraft | grep -E 'common|clientOnly' | grep -v sources | sort -u)
CP=$(echo "$JARS" | tr '\n' ':')
while read -r line; do
  [ -z "$line" ] && continue
  case "$line" in
    methods:*) spec="${line#methods:}"; cls="${spec%%#*}"; names="${spec#*#}"
      echo "== METHODS $cls ($names) =="
      javap -cp "$CP" -p -c "$cls" 2>&1 | awk -v names="$names" '
        BEGIN { n = split(names, arr, ",") }
        /^  [a-z].*\(.*\);$/ || /^  [a-z].*;$/ { show = 0; for (i = 1; i <= n; i++) if (index($0, " " arr[i] "(") > 0) show = 1 }
        show { print }' ;;
    grep:*) pat="${line#grep:}"; cls="${pat%%#*}"; re="${pat#*#}"; echo "== GREP $cls /$re/ =="
      javap -cp "$CP" -p "$cls" 2>&1 | grep -E "$re" ;;
    *) echo "== $line =="; javap -cp "$CP" -p "$line" 2>&1 ;;
  esac
done < .github/api-dump-classes.txt

for j in $(echo "$CP" | tr ':' ' '); do unzip -l "$j" 2>/dev/null | awk '{print $4}' | grep -E 'api/client/gametest/v1/.*\.class$' | grep -v '\$' ; done | sort -u | head -40

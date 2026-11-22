#!/system/bin/sh
for p in $(ps -A -o PID,ARGS | awk '/[f]ixow_guard\/service.sh/ {print $1}'); do
  [ "$p" = "$$" ] || kill "$p" 2>/dev/null
done
sleep 1
nohup sh /data/adb/modules/fixow_guard/service.sh >/dev/null 2>&1 &

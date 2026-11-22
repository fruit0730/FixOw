#!/system/bin/sh
for p in $(ps -A -o PID,ARGS | awk '$2=="sh" && $3=="/data/adb/modules/fixow_guard/service.sh" {print $1}'); do
  kill "$p" 2>/dev/null
done
sleep 1
nohup sh /data/adb/modules/fixow_guard/service.sh >/dev/null 2>&1 &

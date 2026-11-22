#!/system/bin/sh
LOG=/data/adb/fixow_guard.log
while true; do
  if ! pidof lspd >/dev/null 2>&1; then
    echo "$(date '+%Y-%m-%d %H:%M:%S') lspd missing, restarting daemon" >> $LOG
    if [ -x /data/adb/modules/zygisk_lsposed/daemon ]; then
      /data/adb/modules/zygisk_lsposed/daemon >/dev/null 2>&1 &
    fi
    sleep 6
    kill $(pidof com.heytap.wearable.systemui) 2>/dev/null
    kill $(pidof com.heytap.wearable.launcher) 2>/dev/null
    kill $(pidof com.heytap.wearable.healthcenter) 2>/dev/null
    echo "$(date '+%Y-%m-%d %H:%M:%S') bounced UI processes for hook re-injection" >> $LOG
    sleep 6
  fi
  sleep 60
done

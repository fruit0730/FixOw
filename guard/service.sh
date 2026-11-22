#!/system/bin/sh
LOG=/data/adb/fixow_guard.log
STAMP=/data/adb/fixow_guard.lastbounce
while true; do
  if ! pidof lspd >/dev/null 2>&1; then
    echo "$(date '+%Y-%m-%d %H:%M:%S') lspd missing, restarting daemon" >> $LOG
    if [ -x /data/adb/modules/zygisk_lsposed/daemon ]; then
      /data/adb/modules/zygisk_lsposed/daemon >/dev/null 2>&1 &
    fi
    sleep 6
    now=$(date +%s)
    last=0
    [ -f $STAMP ] && last=$(cat $STAMP)
    if [ $((now - last)) -ge 180 ]; then
      echo $now > $STAMP
      kill $(pidof com.heytap.wearable.systemui) 2>/dev/null
      kill $(pidof com.heytap.wearable.launcher) 2>/dev/null
      kill $(pidof com.heytap.wearable.healthcenter) 2>/dev/null
      echo "$(date '+%Y-%m-%d %H:%M:%S') bounced UI processes for hook re-injection" >> $LOG
    else
      echo "$(date '+%Y-%m-%d %H:%M:%S') bounce skipped (cooldown), lspd restarted" >> $LOG
    fi
    sleep 6
  fi
  sleep 60
done

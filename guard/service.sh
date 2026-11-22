#!/system/bin/sh
# FixOw guard: if lspd disappears, restart it and bounce hooked UI processes.
while true; do
  if ! pidof lspd >/dev/null 2>&1; then
    if [ -x /data/adb/modules/zygisk_lsposed/daemon ]; then
      /data/adb/modules/zygisk_lsposed/daemon >/dev/null 2>&1 &
    fi
    sleep 6
    kill $(pidof com.heytap.wearable.systemui) 2>/dev/null
    kill $(pidof com.heytap.wearable.launcher) 2>/dev/null
    kill $(pidof com.heytap.wearable.healthcenter) 2>/dev/null
    sleep 6
  fi
  sleep 60
done

#!/usr/bin/env bash
# 顺序跑 4 个阶梯压测脚本。
# 每个脚本开跑前重新登录一次压测账号：管理端 token 只有 30 分钟 TTL，而单个脚本约 14 分钟。
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
HERE_WIN='E:\GitProject\Flight_management_system_sp\loadtest'
JMETER_BAT='F:\apache-jmeter-5.6.3\apache-jmeter-5.6.3\bin\jmeter.bat'

# 只留分析需要的列，CSV 小一个数量级
SAVE_FLAGS="-Jsample_variables=bizCode \
 -Jjmeter.save.saveservice.thread_name=false \
 -Jjmeter.save.saveservice.bytes=false \
 -Jjmeter.save.saveservice.sent_bytes=false \
 -Jjmeter.save.saveservice.latency=false \
 -Jjmeter.save.saveservice.data_type=false \
 -Jjmeter.save.saveservice.assertion_results_failure_message=false"

PLANS="${*:-01-search 03-admin-read 02-booking 04-mixed}"

mkdir -p "$HERE/logs" "$HERE/results"
for p in $PLANS; do
  echo "=================== $(date '+%F %T') 开始 $p ==================="
  TOKENS=$(PYTHONIOENCODING=utf-8 python "$HERE/lt_api.py" tokens) || { echo "取 token 失败，跳过 $p"; continue; }
  rm -f "$HERE/results/$p.csv"
  cmd //c "$JMETER_BAT -n -t $HERE_WIN\\plans\\$p.jmx -l $HERE_WIN\\results\\$p.csv -j $HERE_WIN\\logs\\$p.log $SAVE_FLAGS $TOKENS"
  echo "=================== $(date '+%F %T') $p 结束 ==================="
done
echo "ALL DONE"

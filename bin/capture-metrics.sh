while sleep 1; do
  echo
  date
  curl marathon.mesos:8080/metrics
done >> artifacts/metrics.log

while sleep 1; do
  date
  curl marathon.mesos:8080/metrics
done >> artifacts/metrics.log

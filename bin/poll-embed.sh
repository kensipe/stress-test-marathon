while sleep 10; do
  date
  echo "Polling embed..."
  curl marathon.mesos:8080/v2/apps?embed=app.failures | wc -c
done

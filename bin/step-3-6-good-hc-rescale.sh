INSTANCES="$1"
for n in {1..500}; do
  date
  echo app $n
  time curl -X PUT marathon.mesos:8080/v2/apps/test-${n} --data '{"instances": '$INSTANCES'}' -H "Content-Type: application/json"
  echo
done

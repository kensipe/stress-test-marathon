# clean the slate

# oh geez don't run this on a prod system
curl marathon.mesos:8080/v2/apps | jq .apps[].id -r | while read app; do curl -X DELETE marathon.mesos:8080/v2/apps/$app; done

sleep 30

if [ $(curl marathon.mesos:8080/v2/apps | jq .apps[].id -r | wc -l) != 0 ]; then
  echo "not scaled down fully"
  exit 1
fi

echo "make sure you have other processes running (capture metrics, poll app embeds); press enter"
read

bin/step-1-launch-500-good-hc.sh 2>&1 | tee -a artifacts/step-1-launch-500-good-hc.log
bin/step-2-launch-400-bad-hc.sh 2>&1 | tee -a artifacts/step-2-launch-400-bad-hc.log
sleep 120
bin/step-3-6-good-hc-rescale.sh 2 2>&1 | tee -a artifacts/step-3-good-hc-to-2.log
sleep 120
bin/step-3-6-good-hc-rescale.sh 3 2>&1 | tee -a artifacts/step-4-good-hc-to-3.log

echo "kill poll app embeds; press enter to continue"
read

bin/step-3-6-good-hc-rescale.sh 2 2>&1 | tee -a artifacts/step-5-good-hc-to-2-no-poll.log
sleep 120
bin/step-3-6-good-hc-rescale.sh 3 2>&1 | tee -a artifacts/step-6-good-hc-to-3-no-poll.log

for n in {1..500}; do
  cat <<-EOF > payload.json
{
  "id": "/test-${n}",
  "cmd": "nc -kl -p 1500 -e sh -c $'sleep 1; echo -e \"HTTP/1.1 200 OK\\\\r\\\\nContent-Length: 3\\\\r\\\\n\\\\r\\\\nHi\"'",
  "env": {},
  "instances": 1,
  "cpus": 0.001,
  "mem": 64,
  "container": {
    "type": "DOCKER",
    "volumes": [],
    "docker": {
      "image": "alpine",
      "network": "BRIDGE",
      "portMappings": [
        {
          "containerPort": 1500,
          "hostPort": 0,
          "servicePort": 10000,
          "protocol": "tcp",
          "name": "http",
          "labels": {}
        }
      ],
      "privileged": false,
      "parameters": [],
      "forcePullImage": false
    }
  },
  "healthChecks": [
    {
      "gracePeriodSeconds": 300,
      "intervalSeconds": 60,
      "timeoutSeconds": 20,
      "maxConsecutiveFailures": 3,
      "portIndex": 0,
      "path": "/test",
      "protocol": "HTTP",
      "delaySeconds": 15
    }
  ]
}
EOF

  date
  echo app $n
  time curl -X PUT marathon.mesos:8080/v2/apps/test-${n} --data @payload.json -H "Content-Type: application/json"
  echo
done

# Intro

This is a collection of helper scripts to run a stress test. It may turn in to
something else. It's explorative work and is loosely organized.

Depending on the value, it will become more organized as such effort is helpful.

# Some notes

You can install homebrew, grafana, logstash, kibana, and influxdb all from homebrew. `brew install` etc.

## grafana:

    /usr/local/opt/grafana/bin/grafana-server --config /usr/local/etc/grafana/grafana.ini --homepath /usr/local/opt/grafana/share/grafana/

## Influx:

    influxd run -config /usr/local/etc/influxdb.conf
    
## Logstash:

To process the log file, edit the path in the conf file, and then run like this:

    logstash -f conf/dcos-marathon-1-4-5.conf
    
To explore new grok filters, edit the config file so it listens on tcp and outputs to rubydebug (console). Comment out elasticsearch output and file input. Then run:

    logstash -f conf/dcos-marathon-1-4-5.conf --config.reload.automatic

You can send a single line to it with netcat;

    head -n 10 mylogfile.log | nc localhost 8000

Note the multiline filters need to be synced

## elasticsearch

Just run `elasticsearch`

Purge all logstash indexes:

    curl localhost:9200/_all | jq '. | keys[]' -r | grep logstash | while read index; do curl -X DELETE localhost:9200/$index; done

## kibana

Just run `kibana`

## R

Install ggplot2 via install.packages("ggplot2")

To make the charts:

    for f in artifacts/step-*.log; do bin/extract-http-response-metrics.sc $f > $f.tsv; done
    for f in artifacts/step-*.tsv; do FILE=$f R --no-save < viz-scripts/scale-single.R; done
    R --no-save < viz-scripts/scale-aggregate.R; done

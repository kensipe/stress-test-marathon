INDICES:=$(shell curl -s localhost:9200/_all | jq '. | keys[]' -r | grep "logstash")
STEPS:=$(foreach F,$(wildcard artifacts/step-*.log),$(notdir $(basename $F)))
.PHONY: load-metrics es-load reset-metrics

output:
	mkdir -p output

output/deploy-data/%.json: lib/get_deploy_data.json
	mkdir -p output/deploy-data
	amm bin/search-scroll.sc $(basename $(@F)) lib/get_deploy_data.json > $@.tmp
	mv $@.tmp $@

output/deploy-data.json: $(foreach INDEX,$(INDICES),output/deploy-data/$(INDEX).json)
	cat output/deploy-data/*.json > $@.tmp
	mv $@.tmp $@

output/deploy-data-filtered.json: output/deploy-data.json
	cat output/deploy-data.json | jq -s '.| map(._source)' > $@.tmp
	mv $@.tmp $@

output/deployments.json: output/deploy-data-filtered.json
	cat output/deploy-data-filtered.json | jq '. | group_by(.planId) | map(map(select(.planId != null)) | .[0].planId as $$planId | map({key: .class2, value: .["@timestamp"]}) | from_entries + {planId: $$planId})' > $@.tmp
	mv $@.tmp $@

output/%.tsv: artifacts/%.log
	bin/extract-http-response-metrics.sc $< > $@.tmp
	mv $@.tmp $@

output/%.svg: output/%.tsv
	cd viz-scripts && FILE=../$< OUTFILE=../$@ R --no-save < scale-single.R

output/all.svg: $(foreach STEP,$(STEPS),output/$(STEP).tsv) $(foreach STEP,$(STEPS),output/$(STEP).svg)
	cd viz-scripts/ && OUTFILE=../$@ R --no-save < scale-aggregate.R

output/dcos-marathon-threaddumps.log output/dcos-marathon-only.log: artifacts/dcos-marathon.log
	mkdir -p output
	bin/seperate-threaddumps.sc $< output/dcos-marathon-only.log.tmp output/dcos-marathon-threaddumps.log.tmp
	mv output/dcos-marathon-only.log.tmp output/dcos-marathon-only.log
	mv output/dcos-marathon-threaddumps.log.tmp output/dcos-marathon-threaddumps.log

output/metrics.txt: artifacts/metrics.log
	bin/metric-snapshots-to-influx-line-format.sc $< > $@.tmp
	mv $@.tmp $@

reset-metrics:
	echo "drop database metrics" | influx
	echo "create database metrics" | influx

load-metrics: output/metrics.txt
	bin/stream-to-influx.sc $< metrics

es-load:
	rm -rf /tmp/es-load
	ln -sf $(PWD) /tmp/es-load

output/load-elasticsearch.db: output/dcos-marathon-only.log | es-load
	logstash -f conf/dcos-marathon-for-loading

logstash-testing:
	logstash -f conf/dcos-marathon-for-output --config.reload.automatic

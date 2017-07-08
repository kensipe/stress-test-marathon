INDICES:=$(shell curl -s localhost:9200/_all | jq '. | keys[]' -r | grep "logstash")
STEPS:=$(foreach F,$(wildcard artifacts/step-*.log),$(notdir $(basename $F)))

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

output/%.pdf: output/%.tsv
	cd viz-scripts && FILE=../$< OUTFILE=../$@ R --no-save < scale-single.R

output/all.pdf: $(foreach STEP,$(STEPS),output/$(STEP).tsv) $(foreach STEP,$(STEPS),output/$(STEP).pdf)
	cd viz-scripts/ && OUTFILE=../$@ R --no-save < scale-aggregate.R

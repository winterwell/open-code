
# /data the endpoint for analysing data


start defaults to start of day one week ago
end defaults to now (quantized by 10 minutes)

Breakdown: Format: bucket-by-fields/ {"report-fields": "operation"} 
	 * 	e.g. "evt" or "evt/time" or "tag/time {"mycount":"avg"}"
	 * NB: the latter part is optional, but if present must be valid json.

An example call:
	 
https://lg.good-loop.com/data?t=pixel&d=emissions&breakdown=adid{"co2":"sum"}
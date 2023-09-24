create:
	k3d cluster create --config cluster-config.yaml
delete:
	k3d cluster delete --config cluster-config.yaml

build-clients:
	$(MAKE) -C clients/02-tracing-go image
	$(MAKE) -C clients/02-tracing-java image
	$(MAKE) -C clients/02-tracing-js image

	k3d image import kalli.dev/02-tracing-go:latest
	k3d image import kalli.dev/02-tracing-java:latest
	k3d image import kalli.dev/02-tracing-js:latest

	kubectl -n demo rollout restart deployment

import-clients:
	k3d image import kalli.dev/02-tracing-go:latest
	k3d image import kalli.dev/02-tracing-java:latest
	k3d image import kalli.dev/02-tracing-js:latest

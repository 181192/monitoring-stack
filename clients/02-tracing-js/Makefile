IMAGE_NAME=kalli.dev/02-tracing-js
IMAGE_TAG=latest

image:
	docker build -t $(IMAGE_NAME):$(IMAGE_TAG) .

run:
	docker run --rm -p 8083:8080 $(IMAGE_NAME):$(IMAGE_TAG)

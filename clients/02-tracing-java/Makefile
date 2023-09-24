IMAGE_NAME=kalli.dev/02-tracing-java
IMAGE_TAG=latest

.PHONY: image, run
image:
	./gradlew bootBuildImage --imageName=$(IMAGE_NAME):$(IMAGE_TAG)

run:
	docker run --rm -p 8080:8080 --env-file .env $(IMAGE_NAME):$(IMAGE_TAG)

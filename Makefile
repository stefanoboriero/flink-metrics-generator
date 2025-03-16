
.PHONY: all
all: build run

.PHONY: build
build:
	mvn clean package

.PHONY: run
run:
	docker compose up

.PHONY: stop
stop:
	docker compose down

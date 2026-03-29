.PHONY: up down logs test build clean seed

up:
	docker compose up -d

down:
	docker compose down

logs:
	docker compose logs -f app

test:
	mvn test

build:
	mvn clean package -DskipTests

clean:
	docker compose down -v

seed:
	@echo "Seed data is applied automatically by Flyway on startup (V2__seed_data.sql)."
	@echo "Run 'make up' to start the stack — migrations run before the app accepts traffic."

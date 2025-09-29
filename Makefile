SHELL := /bin/bash
.PHONY: backend-package docker-build docker-run compose-up seed-h2

backend-package:
	mvn -f backend/pom.xml -DskipTests package

docker-build: backend-package
	docker build -f backend/Dockerfile -t reloader-backend:local .

docker-run: docker-build
	docker run -e RELOADER_USE_H2_EXTERNAL=true -e EXTERNAL_DB_ALLOW_WRITES=true -e SPRING_PROFILES_ACTIVE=dev -p 8005:8080 --name reloader-local reloader-backend:local

compose-up:
	docker compose up --build

seed-h2:
	@echo "Seeding H2 (file-backed) using backend/scripts/docker-seed-h2.sh"
	backend/scripts/docker-seed-h2.sh

seed-h2-windows:
	@echo "Seeding H2 on Windows (PowerShell) using backend/scripts/docker-seed-h2.ps1"
	@if command -v pwsh >/dev/null 2>&1; then \
		pwsh -NoProfile -ExecutionPolicy Bypass -File backend/scripts/docker-seed-h2.ps1; \
	elif command -v powershell >/dev/null 2>&1; then \
		powershell -ExecutionPolicy Bypass -File backend/scripts/docker-seed-h2.ps1; \
	else \
		echo "No PowerShell found on PATH. Install PowerShell or run the script manually."; exit 2; \
	fi

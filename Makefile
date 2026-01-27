.PHONY: install s
install:
	@echo "Installing Backend dependencies..."
	cd backend && pip install -r requirements.txt || pip install fastapi uvicorn trimesh python-multipart numpy
	@echo "Installing Frontend dependencies..."
	cd frontend && npm install

start:
	@echo "Starting development environment..."
	./start.sh

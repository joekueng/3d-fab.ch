FROM node:24.21.0-bookworm

WORKDIR /app
COPY package*.json ./
RUN npm ci --no-audit --no-fund --prefer-offline \
    && npx playwright install --with-deps chromium firefox webkit
COPY . .

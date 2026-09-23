FROM node:22-alpine

WORKDIR /app
COPY package*.json ./
RUN npm ci --legacy-peer-deps
COPY . .
ARG BUILD_CONFIGURATION=production
RUN npm run build -- --configuration="${BUILD_CONFIGURATION}"

ENV NODE_ENV=production
ENV PORT=80
EXPOSE 80

CMD ["node", "dist/frontend/server/server.mjs"]

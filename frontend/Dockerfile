FROM node:24.21.0-alpine AS build

WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ARG BUILD_CONFIGURATION=production
RUN npm run build -- --configuration="${BUILD_CONFIGURATION}"

FROM node:24.21.0-alpine AS runtime

WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev --ignore-scripts \
    && npm cache clean --force
COPY --from=build /app/dist ./dist

ENV NODE_ENV=production
ENV PORT=80
EXPOSE 80

USER node

CMD ["node", "dist/frontend/server/server.mjs"]

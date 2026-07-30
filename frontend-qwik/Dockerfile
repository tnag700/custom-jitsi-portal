FROM node:24.18.0-alpine AS build

WORKDIR /app

COPY package.json package-lock.json .npmrc ./
RUN npm ci

COPY . .

ARG VITE_API_URL=http://localhost:8080/api/v1
ENV VITE_API_URL=${VITE_API_URL}

RUN npm run build

FROM node:24.18.0-alpine

WORKDIR /app

COPY --from=build /app/dist ./dist
COPY server/package.json server/package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts
COPY --from=build /app/server/entry.express.mjs ./server/entry.express.mjs

RUN addgroup -S app && adduser -S -G app -h /app app \
	&& chown -R app:app /app

ENV PORT=3000
EXPOSE 3000

USER app

CMD ["node", "server/entry.express.mjs"]

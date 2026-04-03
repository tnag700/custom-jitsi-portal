FROM node:22-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

ARG VITE_API_URL=http://localhost:8080/api/v1
ENV VITE_API_URL=${VITE_API_URL}

RUN npm run build

FROM node:22-alpine

WORKDIR /app

COPY --from=build /app/dist ./dist
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/server ./server

RUN addgroup -S app && adduser -S -G app -h /app app \
	&& chown -R app:app /app

ENV PORT=3000
EXPOSE 3000

USER app

CMD ["node", "server/entry.express.mjs"]

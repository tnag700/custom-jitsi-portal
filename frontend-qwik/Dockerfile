FROM node:24.21.0-alpine@sha256:ebfe2f90462722a7a4de65e91990e97fe0d401c70e0e762c5b53302f905ec1c1 AS build

WORKDIR /app

COPY package.json package-lock.json .npmrc ./
RUN npm ci

COPY . .

ARG VITE_API_URL=http://localhost:8080/api/v1
ENV VITE_API_URL=${VITE_API_URL}

RUN npm run build

FROM node:24.21.0-alpine@sha256:ebfe2f90462722a7a4de65e91990e97fe0d401c70e0e762c5b53302f905ec1c1

WORKDIR /app

COPY --from=build /app/dist ./dist
COPY --from=build /app/server/build ./server/build
COPY server/package.json server/package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts
COPY --from=build /app/server/entry.express.mjs ./server/entry.express.mjs

RUN addgroup -S app && adduser -S -G app -h /app app \
	&& chown -R app:app /app

ENV PORT=3000
EXPOSE 3000

USER app

CMD ["node", "server/entry.express.mjs"]

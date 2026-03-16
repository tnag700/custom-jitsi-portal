FROM node:22-alpine

WORKDIR /app

COPY dist ./dist
COPY node_modules ./node_modules
COPY server ./server

ENV PORT=3000
EXPOSE 3000

CMD ["node", "server/entry.express.mjs"]

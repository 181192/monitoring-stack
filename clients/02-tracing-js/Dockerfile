FROM node:18.14-alpine AS builder

WORKDIR /app

COPY package*.json ./

RUN npm install --only=production

COPY . .

CMD ["node",  "-r", "/app/tracing.js", "/app/main.js"]

FROM node:20-alpine
WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund
COPY bench.js ./
ENTRYPOINT ["node", "bench.js"]
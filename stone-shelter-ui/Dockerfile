FROM node:24.21.0-bookworm-slim@sha256:d6aa754f16b3197301076f047b5def2f02ea1dbbc2ca920407d46d7ec7f87b20
WORKDIR /app
RUN chown node:node /app
USER node
RUN test "$(node --version)" = "v24.21.0" && test "$(npm --version)" = "11.19.0"
COPY --chown=node:node package.json package-lock.json ./
RUN npm ci --no-audit --no-fund && npm cache clean --force
COPY --chown=node:node . .
RUN npm run build
EXPOSE 5173
CMD ["npm", "run", "dev:api", "--", "--host", "0.0.0.0", "--port", "5173", "--strictPort"]

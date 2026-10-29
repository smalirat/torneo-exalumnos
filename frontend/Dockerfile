# syntax=docker/dockerfile:1
# Frontend (Vue + Vite). Node builds the static files, nginx serves them and forwards /api to the backend.

# ---- build
# On the build machine's platform: the output (HTML/JS/CSS) is the same for every CPU
FROM --platform=$BUILDPLATFORM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run build

# ---- serve
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
# 127.0.0.1, not localhost: inside the container localhost resolves to ::1 first and nginx only listens on IPv4
HEALTHCHECK --interval=15s --timeout=5s --retries=5 CMD wget -qO- http://127.0.0.1/ > /dev/null || exit 1

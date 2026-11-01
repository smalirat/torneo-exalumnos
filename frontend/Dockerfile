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
# Stable branch (even minor version); the tag follows its patch releases. apk upgrade adds the Alpine security
# fixes published after the base image was built (CI scans the result, see .github/workflows/security.yml)
FROM nginx:1.30-alpine
# Run nginx entirely as the unprivileged nginx user, not as root: no "user" switch, the pid file in /tmp, and its cache
# folders owned by nginx. That is why it listens on 8080 (ports below 1024 need root)
RUN apk upgrade --no-cache \
    && sed -i -e '/^user /d' -e 's#^pid .*#pid /tmp/nginx.pid;#' /etc/nginx/nginx.conf \
    && chown -R nginx:nginx /var/cache/nginx
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY api-proxy.inc /etc/nginx/conf.d/api-proxy.inc
COPY --from=build /app/dist /usr/share/nginx/html
USER nginx
EXPOSE 8080
# 127.0.0.1, not localhost: inside the container localhost resolves to ::1 first and nginx only listens on IPv4
HEALTHCHECK --interval=15s --timeout=5s --retries=5 CMD wget -qO- http://127.0.0.1:8080/ > /dev/null || exit 1

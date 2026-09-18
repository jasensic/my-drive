# syntax=docker/dockerfile:1
FROM node:22-bookworm-slim AS build
WORKDIR /app
COPY apps/web/package.json apps/web/package-lock.json ./
RUN npm ci
COPY apps/web/ ./
ARG PRIMENG_LICENSE=
ENV PRIMENG_LICENSE=$PRIMENG_LICENSE
# Compose interpolation looks for .env next to the compose file (deploy/), so local
# builds also copy the repo-root .env when it exists in the build context.
RUN --mount=type=bind,source=.,target=/src \
    if [ -f /src/.env ]; then cp /src/.env /app/.env; fi && \
    npm run build

FROM nginx:1.27-alpine
ENV NGINX_RESOLVER=127.0.0.11
ENV API_UPSTREAM_HOST=api:8080
COPY deploy/nginx/nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/web/browser /usr/share/nginx/html
EXPOSE 80

# syntax=docker/dockerfile:1

FROM node:22-bookworm-slim AS build
WORKDIR /app
COPY kiwi-admin/frontend/package.json kiwi-admin/frontend/package-lock.json ./
RUN npm ci
COPY kiwi-admin/frontend .
ENV NODE_OPTIONS=--max_old_space_size=8192
RUN npm run build:docker

FROM nginx:1.27-alpine
COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html/kiwi-admin
EXPOSE 80

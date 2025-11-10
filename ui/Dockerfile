# ---------- Stage 1: build React ----------
FROM node:20-alpine AS build
WORKDIR /app

# 🧠 Устанавливаем только то, что нужно для установки зависимостей
COPY package.json yarn.lock* ./
RUN yarn install --frozen-lockfile --network-timeout 100000

# Собираем проект
COPY . .
ARG REACT_APP_BACKEND_URL=http://swp-backend:8111
ENV REACT_APP_BACKEND_URL=$REACT_APP_BACKEND_URL
ENV PUBLIC_URL=/
RUN yarn build && \
    rm -rf node_modules

# ---------- Stage 2: Nginx (подача статических файлов) ----------
FROM nginx:1.25-alpine

# Чистим дефолтный сайт
RUN rm -rf /usr/share/nginx/html/* && \
    rm -f /etc/nginx/conf.d/default.conf

# Копируем сборку и конфиг nginx
COPY --from=build /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost/ || exit 1

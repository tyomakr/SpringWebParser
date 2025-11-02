# ---------- Stage 1: build React ----------
FROM node:20-alpine AS build
WORKDIR /app

# Копируем файлы зависимостей для кеширования слоёв
COPY package.json yarn.lock* ./
RUN yarn install --frozen-lockfile --network-timeout 100000

# Копируем исходники и собираем
COPY . .
ARG REACT_APP_API_URL=/
ENV REACT_APP_API_URL=$REACT_APP_API_URL
ENV PUBLIC_URL=/
RUN yarn build && \
    rm -rf node_modules

# ---------- Stage 2: Nginx (оптимизированный) ----------
FROM nginx:1.25-alpine

# Удаляем ненужные файлы из базового образа
RUN rm -rf /usr/share/nginx/html/* && \
    rm -f /etc/nginx/conf.d/default.conf

# Копируем собранное приложение
COPY --from=build /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost/ || exit 1

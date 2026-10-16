FROM node:22-alpine AS build

WORKDIR /site

COPY package.json package-lock.json ./
RUN npm ci

COPY docs/ ./docs/

RUN npm run docs:build

FROM nginx:stable-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /site/docs/.vitepress/dist/ /usr/share/nginx/html/

EXPOSE 80

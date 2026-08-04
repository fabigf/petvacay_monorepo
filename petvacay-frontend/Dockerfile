# Etapa de desarrollo: servidor de Vite con recarga en caliente (docker compose up).
FROM node:22-alpine AS development
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
EXPOSE 3000
CMD ["npm", "run", "dev", "--", "--host"]

# ---------------------------------------------------------------- produccion
FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# Nginx sirve los estaticos y hace de reverse proxy hacia el backend / gateway.
FROM nginx:1.27-alpine AS production
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

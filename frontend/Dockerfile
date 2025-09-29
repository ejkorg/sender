# Multi-stage build for Angular frontend
FROM node:20-bullseye AS build
WORKDIR /app

# Copy source
COPY frontend/package*.json ./
COPY frontend/ ./

# Install & build
RUN npm ci --silent && npm run build -- --configuration=production

# Serve with nginx
FROM nginx:1.26-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json ./
RUN npm install

FROM node:20-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ARG NEXT_PUBLIC_INCIDENT_API
ARG NEXT_PUBLIC_REALTIME_API
ENV NEXT_PUBLIC_INCIDENT_API=$NEXT_PUBLIC_INCIDENT_API
ENV NEXT_PUBLIC_REALTIME_API=$NEXT_PUBLIC_REALTIME_API
RUN npm run build

FROM node:20-alpine AS run
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app .
EXPOSE 3000
CMD ["npm", "run", "start"]

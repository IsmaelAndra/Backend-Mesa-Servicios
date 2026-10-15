# Puente de telefonía de la Mesa de Servicios COAC (ver README.md).
# Sin dependencias: solo Node.js. Corre sin root y no expone puertos.
FROM node:20-alpine

WORKDIR /app
COPY index.mjs ./

USER node
ENV NODE_ENV=production TZ=America/Guayaquil
CMD ["node", "index.mjs"]

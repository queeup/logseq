# NOTE: please keep it in sync with .github pipelines
# NOTE: during testing make sure to change the branch below
# NOTE: before running the build-docker GH action edit
#       build-docker.yml and change the release channel from :latest to :testing

# Builder image
FROM clojure:temurin-11-tools-deps-1.11.1.1413-bullseye-slim AS builder

ARG DEBIAN_FRONTEND=noninteractive

# Install reqs
RUN apt-get update && apt-get install -y --no-install-recommends \
    rsync \
    curl \
    ca-certificates \
    apt-transport-https \
    gpg \
    build-essential libcairo2-dev libpango1.0-dev libjpeg-dev libgif-dev librsvg2-dev

# install NodeJS & yarn
RUN curl -sL https://deb.nodesource.com/setup_18.x | bash -

RUN curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | gpg --dearmor | \
    tee /etc/apt/trusted.gpg.d/yarn.gpg && \
    echo "deb https://dl.yarnpkg.com/debian/ stable main" | \
    tee /etc/apt/sources.list.d/yarn.list && \
    apt-get update && apt-get install -y nodejs yarn

WORKDIR /data

# build Logseq static resources
RUN git clone -b feat/db https://github.com/logseq/logseq.git .

RUN yarn config set network-timeout 240000 -g && yarn install --frozen-lockfile

RUN yarn gulp:build && \
    clojure -M:cljs release app  --config-merge '{:compiler-options {:source-map-include-sources-content false :source-map-detail-level :symbols}}' && \
    rsync -avz --exclude node_modules --exclude android --exclude ios ./static/ ./public/static/ && \
    ls -lR ./public

# Web App Runner image
FROM nginx:1.27.0-alpine3.19

COPY --from=builder /data/public/static/ /usr/share/nginx/html


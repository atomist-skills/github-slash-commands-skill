FROM gcr.io/atomist-container-skills/clojure-base:0.0.1

WORKDIR /usr/src/app

RUN curl -L -O https://github.com/cli/cli/releases/download/v0.10.1/gh_0.10.1_linux_amd64.deb
RUN apt install ./gh_0.10.1_linux_amd64.deb

COPY package*.json /usr/src/app/
RUN npm ci
COPY deps.edn shadow-cljs.edn /usr/src/app/
COPY src /usr/src/app/src
RUN npm run build

ENTRYPOINT [ "node" ]
CMD [ "index.js" ]

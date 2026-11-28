FROM node:26-bookworm AS builder
WORKDIR /src
ARG EV_SOURCE=https://codeload.github.com/cars10/elasticvue/tar.gz/69d1fd251ee8aeda0478c21470f6d4876b1a8832
ADD ${EV_SOURCE} /tmp/elasticvue.tgz
RUN echo "29e80fe56fc51e0b6675cf82933001211cb27fcf52bbf473e0556206f543c4bd  /tmp/elasticvue.tgz" | sha256sum -c - \
    && tar -xzf /tmp/elasticvue.tgz --strip-components=1
COPY tianshu.patch /tmp/tianshu.patch
RUN git apply /tmp/tianshu.patch && npm ci --no-audit --no-fund && npm run tsc && npm run test:unit
RUN NODE_ENV=production VITE_APP_BUILD_MODE=other VITE_APP_VARIANT=beta VITE_APP_PUBLIC_PATH=/es/ npm run build -- --sourcemap=false

FROM cars10/elasticvue@sha256:23fb4dc7095c66690fd26a49d07a1a1e38ac436247f1351c8b1a17a85f3585a8
COPY --from=builder /src/dist /usr/share/nginx/html/es
COPY --from=builder /src/LICENSE /usr/share/licenses/elasticvue/LICENSE
COPY nginx.conf /etc/nginx/conf.d/elasticvue.conf

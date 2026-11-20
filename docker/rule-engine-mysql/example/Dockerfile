ARG JAVA_IMAGE=eclipse-temurin:17-jre-jammy
FROM ${JAVA_IMAGE}
RUN sed -i "s|http://|https://|g" /etc/apt/sources.list && apt-get -o Acquire::Retries=3 update && DEBIAN_FRONTEND=noninteractive apt-get -o Acquire::Retries=3 install -y --no-install-recommends openssh-server && rm -f /etc/ssh/ssh_host_* && rm -r /var/lib/apt/lists/* && useradd --no-create-home --home-dir / --shell /usr/sbin/nologin pboc_sftp
COPY sshd_config /etc/ssh/sshd_config_example
ENTRYPOINT ["java","-XX:ActiveProcessorCount=2","-Xmx256m","-jar","/app/server.jar","--credit-example-supplier"]

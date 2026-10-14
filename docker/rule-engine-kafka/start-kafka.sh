#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
# 限定 JAAS/Properties 安全字符，拒绝换行或引号配置注入；密码请用 openssl rand -hex 32 生成。
[[ "${KAFKA_USERNAME:-}" =~ ^[a-zA-Z0-9_-]+$ ]] || { echo "Invalid KAFKA_USERNAME" >&2; exit 1; }
[[ "${KAFKA_PASSWORD:-}" =~ ^[a-zA-Z0-9_-]{32,}$ ]] || { echo "KAFKA_PASSWORD needs at least 32 safe random characters" >&2; exit 1; }
[[ "${KAFKA_PASSWORD}" != replace-with-* ]] || { echo "Replace KAFKA_PASSWORD" >&2; exit 1; }
[[ "${KAFKA_ADVERTISED_HOST:-}" =~ ^[a-zA-Z0-9.-]+$ ]] || { echo "Invalid KAFKA_ADVERTISED_HOST" >&2; exit 1; }
[[ "${KAFKA_ADVERTISED_HOST}" != replace-with-* ]] || { echo "Replace KAFKA_ADVERTISED_HOST" >&2; exit 1; }
[[ "${KAFKA_HOST_PORT:-}" =~ ^[0-9]+$ ]] && (( KAFKA_HOST_PORT > 0 && KAFKA_HOST_PORT < 65536 )) || exit 1
cat > /tmp/tianshu-server.properties <<EOF
process.roles=broker,controller
node.id=1
controller.quorum.voters=1@localhost:9093
controller.listener.names=CONTROLLER
listeners=INTERNAL://:9092,EXTERNAL://:9094,CONTROLLER://127.0.0.1:9093
advertised.listeners=INTERNAL://kafka:9092,EXTERNAL://${KAFKA_ADVERTISED_HOST}:${KAFKA_HOST_PORT}
listener.security.protocol.map=INTERNAL:PLAINTEXT,EXTERNAL:SASL_PLAINTEXT,CONTROLLER:PLAINTEXT
inter.broker.listener.name=INTERNAL
sasl.enabled.mechanisms=PLAIN
listener.name.external.plain.sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="${KAFKA_USERNAME}" password="${KAFKA_PASSWORD}" user_${KAFKA_USERNAME}="${KAFKA_PASSWORD}";
log.dirs=/var/lib/kafka/data
num.partitions=1
num.network.threads=2
num.io.threads=2
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
group.initial.rebalance.delay.ms=0
auto.create.topics.enable=false
log.retention.hours=24
log.retention.bytes=268435456
log.segment.bytes=67108864
socket.request.max.bytes=10485760
EOF
cat > /tmp/tianshu-client.properties <<EOF
security.protocol=PLAINTEXT
EOF
if [[ ! -f /var/lib/kafka/data/meta.properties ]]; then
    /opt/kafka/bin/kafka-storage.sh format --cluster-id "$(/opt/kafka/bin/kafka-storage.sh random-uuid)" --config /tmp/tianshu-server.properties
fi
exec /opt/kafka/bin/kafka-server-start.sh /tmp/tianshu-server.properties

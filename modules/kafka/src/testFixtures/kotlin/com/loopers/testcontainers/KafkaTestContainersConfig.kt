package com.loopers.testcontainers

import org.springframework.context.annotation.Configuration
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName

/**
 * Kafka 통합 테스트용 Testcontainers 설정 — MySql/Redis 픽스처와 같은 패턴.
 * 컨테이너는 static 싱글턴으로 1회만 기동해 테스트 클래스 간 재사용하고,
 * 컨텍스트가 뜨기 전에 bootstrap-servers 를 시스템 프로퍼티로 주입한다.
 * cp-kafka 7.5 = Kafka 3.5 — 운영 인프라(docker/infra-compose.yml)와 브로커 버전을 맞춘다.
 */
@Configuration
class KafkaTestContainersConfig {
    companion object {
        private val kafkaContainer = ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.3"))
            .apply {
                start()
            }

        init {
            System.setProperty("spring.kafka.bootstrap-servers", kafkaContainer.bootstrapServers)
            // kafka.yml 이 admin bootstrap 을 도커 네트워크 주소(kafka:9092)로 고정하므로 테스트 컨테이너로 덮어쓴다.
            System.setProperty("spring.kafka.admin.properties.bootstrap.servers", kafkaContainer.bootstrapServers)
        }

        fun bootstrapServers(): String = kafkaContainer.bootstrapServers
    }
}

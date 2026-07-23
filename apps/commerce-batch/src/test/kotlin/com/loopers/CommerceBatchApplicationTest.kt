package com.loopers

import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

// 테스트에서는 잡 자동 실행 러너를 끈다 — 켜 두면 spring.batch.job.name(기본 NONE)에 해당하는 잡이 없어 컨텍스트 기동이 실패한다.
@SpringBootTest(properties = ["spring.batch.job.enabled=false"])
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
class CommerceBatchApplicationTest {
    @Test
    fun contextLoads() {}
}

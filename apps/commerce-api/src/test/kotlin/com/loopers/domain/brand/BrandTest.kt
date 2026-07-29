package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class BrandTest {
    @DisplayName("Brand 를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("name 이 blank 면 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", " ", "   "])
        fun throwsException_whenNameIsBlank(blankName: String) {
            // when
            val result = assertThrows<CoreException> {
                Brand.create(name = blankName)
            }

            // then
            assertThat(result.errorType).isEqualTo(BrandErrorType.BRAND_BAD_REQUEST)
        }
    }

    @DisplayName("Brand 를 soft delete 할 때, ")
    @Nested
    inner class SoftDelete {
        @DisplayName("softDelete() 호출 시 deletedAt 이 설정된다.")
        @Test
        fun setsDeletedAt_whenSoftDeleteIsCalled() {
            // given
            val brand = Brand.create(name = "Nike")

            // when
            brand.softDelete()

            // then
            assertThat(brand.deletedAt).isNotNull()
        }

        @DisplayName("softDelete() 된 Brand 는 isDeleted() 가 true 다.")
        @Test
        fun isDeletedReturnsTrue_afterSoftDelete() {
            // given
            val brand = Brand.create(name = "Nike")

            // when
            brand.softDelete()

            // then
            assertThat(brand.isDeleted()).isTrue()
        }
    }

    @DisplayName("Brand 를 update 할 때, ")
    @Nested
    inner class Update {
        @DisplayName("update(name) 호출 시 name 이 갱신된다.")
        @Test
        fun updatesName() {
            // given
            val brand = Brand.create(name = "Nike")

            // when
            brand.update(name = "Adidas")

            // then
            assertThat(brand.name.value).isEqualTo("Adidas")
        }
    }
}

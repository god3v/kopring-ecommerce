package com.loopers.domain.product

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ProductTest {
    @DisplayName("Product 를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("price 가 음수면 예외가 발생한다.")
        @Test
        fun throwsException_whenPriceIsNegative() {
            // when
            val result = assertThrows<CoreException> {
                Product.create(
                    name = "T-shirt",
                    price = -1,
                    stock = 10,
                    likeCount = 0L,
                    brandId = 1L,
                )
            }

            // then
            assertThat(result.errorType).isEqualTo(ProductErrorType.PRODUCT_BAD_REQUEST)
        }

        @DisplayName("stock 이 음수면 예외가 발생한다.")
        @Test
        fun throwsException_whenStockIsNegative() {
            // when
            val result = assertThrows<CoreException> {
                Product.create(
                    name = "T-shirt",
                    price = 1000,
                    stock = -1,
                    likeCount = 0L,
                    brandId = 1L,
                )
            }

            // then
            assertThat(result.errorType).isEqualTo(ProductErrorType.PRODUCT_BAD_REQUEST)
        }

        @DisplayName("likeCount 가 음수면 예외가 발생한다.")
        @Test
        fun throwsException_whenLikeCountIsNegative() {
            // when
            val result = assertThrows<CoreException> {
                Product.create(
                    name = "T-shirt",
                    price = 1000,
                    stock = 10,
                    likeCount = -1L,
                    brandId = 1L,
                )
            }

            // then
            assertThat(result.errorType).isEqualTo(ProductErrorType.PRODUCT_BAD_REQUEST)
        }

        @DisplayName("brandId 가 음수면 예외가 발생한다.")
        @Test
        fun throwsException_whenBrandIdIsNegative() {
            // when
            val result = assertThrows<CoreException> {
                Product.create(
                    name = "T-shirt",
                    price = 1000,
                    stock = 10,
                    likeCount = 0L,
                    brandId = -1L,
                )
            }

            // then
            assertThat(result.errorType).isEqualTo(ProductErrorType.PRODUCT_BAD_REQUEST)
        }

        @DisplayName("name 이 blank 면 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", " ", "   "])
        fun throwsException_whenNameIsBlank(blankName: String) {
            // when
            val result = assertThrows<CoreException> {
                Product.create(
                    name = blankName,
                    price = 1000,
                    stock = 10,
                    likeCount = 0L,
                    brandId = 1L,
                )
            }

            // then
            assertThat(result.errorType).isEqualTo(ProductErrorType.PRODUCT_BAD_REQUEST)
        }

        @DisplayName("Product.create() 결과의 salesStatus 는 ON_SALE 이 기본이다.")
        @Test
        fun salesStatusDefaultsToOnSale() {
            // when
            val product = Product.create(
                name = "T-shirt",
                price = 1000,
                stock = 10,
                likeCount = 0L,
                brandId = 1L,
            )

            // then
            assertThat(product.salesStatus).isEqualTo(SalesStatus.ON_SALE)
        }
    }

    @DisplayName("재고를 차감할 때, ")
    @Nested
    inner class DeductStock {
        @DisplayName("양수 quantity 로 호출하면 stock 이 그만큼 줄어든다.")
        @Test
        fun reducesStock_whenQtyIsPositive() {
            // given
            val product = Product.create(
                name = "T-shirt",
                price = 1000,
                stock = 10,
                likeCount = 0L,
                brandId = 1L,
            )

            // when
            product.deductStock(3)

            // then
            assertThat(product.stock.value).isEqualTo(7)
        }

        @DisplayName("quantity 가 stock 과 같으면 호출 결과로 stock 이 0 이 된다.")
        @Test
        fun stockBecomesZero_whenQtyEqualsStock() {
            // given
            val product = Product.create(
                name = "T-shirt",
                price = 1000,
                stock = 5,
                likeCount = 0L,
                brandId = 1L,
            )

            // when
            product.deductStock(5)

            // then
            assertThat(product.stock.value).isEqualTo(0)
        }

        @DisplayName("quantity 가 stock 보다 크면 INSUFFICIENT_STOCK 예외가 발생한다.")
        @Test
        fun throwsInsufficientStock_whenQtyExceedsStock() {
            // given
            val product = Product.create(
                name = "T-shirt",
                price = 1000,
                stock = 5,
                likeCount = 0L,
                brandId = 1L,
            )

            // when
            val result = assertThrows<CoreException> {
                product.deductStock(6)
            }

            // then
            assertThat(result.errorType).isEqualTo(ProductErrorType.INSUFFICIENT_STOCK)
        }

        @DisplayName("quantity 가 0 이거나 음수면 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = [0, -1, -10])
        fun throwsException_whenQuantityIsZeroOrNegative(invalidQuantity: Int) {
            // given
            val product = Product.create(
                name = "T-shirt",
                price = 1000,
                stock = 10,
                likeCount = 0L,
                brandId = 1L,
            )

            // when
            val result = assertThrows<CoreException> {
                product.deductStock(invalidQuantity)
            }

            // then
            assertThat(result.errorType).isEqualTo(ProductErrorType.PRODUCT_BAD_REQUEST)
        }
    }

    @DisplayName("Product 를 soft delete 할 때, ")
    @Nested
    inner class SoftDelete {
        @DisplayName("softDelete() 호출 시 deletedAt 이 설정된다.")
        @Test
        fun setsDeletedAt_whenSoftDeleteIsCalled() {
            // given
            val product = Product.create(
                name = "T-shirt",
                price = 1000,
                stock = 10,
                likeCount = 0L,
                brandId = 1L,
            )

            // when
            product.softDelete()

            // then
            assertThat(product.deletedAt).isNotNull()
        }

        @DisplayName("softDelete() 된 Product 는 isDeleted() 가 true 다.")
        @Test
        fun isDeletedReturnsTrue_afterSoftDelete() {
            // given
            val product = Product.create(
                name = "T-shirt",
                price = 1000,
                stock = 10,
                likeCount = 0L,
                brandId = 1L,
            )

            // when
            product.softDelete()

            // then
            assertThat(product.isDeleted()).isTrue()
        }

        @DisplayName("이미 삭제된 Product 의 softDelete() 재호출은 멱등이다 — deletedAt 이 변하지 않는다.")
        @Test
        fun isIdempotent_whenAlreadyDeleted() {
            // given
            val product = Product.create(
                name = "T-shirt",
                price = 1000,
                stock = 10,
                likeCount = 0L,
                brandId = 1L,
            )
            product.softDelete()
            val firstDeletedAt = product.deletedAt

            // when
            product.softDelete()

            // then
            assertThat(product.deletedAt).isEqualTo(firstDeletedAt)
        }
    }

    @DisplayName("Product 를 update 할 때, ")
    @Nested
    inner class Update {
        @DisplayName("name · price · salesStatus 가 갱신된다.")
        @Test
        fun updatesFields() {
            // given
            val product = Product.create(
                name = "T-shirt",
                price = 1000,
                stock = 10,
                likeCount = 0L,
                brandId = 1L,
            )

            // when
            product.update(
                name = "New Name",
                price = 2000,
                salesStatus = SalesStatus.OFF_SALE,
            )

            // then
            assertThat(product.name.value).isEqualTo("New Name")
            assertThat(product.price.value).isEqualTo(2000)
            assertThat(product.salesStatus).isEqualTo(SalesStatus.OFF_SALE)
        }

        @DisplayName("update 는 brandId 를 변경하지 않는다 (등록 이후 불변).")
        @Test
        fun doesNotChangeBrandId() {
            // given
            val originalBrandId = 99L
            val product = Product.create(
                name = "T-shirt",
                price = 1000,
                stock = 10,
                likeCount = 0L,
                brandId = originalBrandId,
            )

            // when
            product.update(
                name = "New Name",
                price = 2000,
                salesStatus = SalesStatus.OFF_SALE,
            )

            // then
            assertThat(product.brandId).isEqualTo(originalBrandId)
        }
    }

    @DisplayName("likeCount 토글")
    @Nested
    inner class LikeCountToggle {
        @DisplayName("increaseLikeCount() 호출 시 likeCount 가 1 증가한다.")
        @Test
        fun increaseLikeCountByOne() {
            val product = Product.create(name = "T", price = 1000, stock = 10, likeCount = 5L, brandId = 1L)

            product.increaseLikeCount()

            assertThat(product.likeCount).isEqualTo(6L)
        }

        @DisplayName("decreaseLikeCount() 호출 시 likeCount 가 1 감소한다.")
        @Test
        fun decreaseLikeCountByOne() {
            val product = Product.create(name = "T", price = 1000, stock = 10, likeCount = 5L, brandId = 1L)

            product.decreaseLikeCount()

            assertThat(product.likeCount).isEqualTo(4L)
        }

        @DisplayName("likeCount 가 0 일 때 decreaseLikeCount() 는 0 을 유지한다 (음수 방지 멱등).")
        @Test
        fun decreaseLikeCountDoesNotGoBelowZero() {
            val product = Product.create(name = "T", price = 1000, stock = 10, likeCount = 0L, brandId = 1L)

            product.decreaseLikeCount()

            assertThat(product.likeCount).isEqualTo(0L)
        }
    }

    @DisplayName("restoreStock")
    @Nested
    inner class RestoreStock {
        @DisplayName("양수 quantity 로 호출하면 stock 이 그만큼 증가한다.")
        @Test
        fun restoresStockByPositiveQuantity() {
            val product = Product.create(name = "T", price = 1000, stock = 5, likeCount = 0L, brandId = 1L)

            product.restoreStock(3)

            assertThat(product.stock.value).isEqualTo(8)
        }

        @DisplayName("0 이하의 quantity 는 PRODUCT_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsWhenQuantityNonPositive() {
            val product = Product.create(name = "T", price = 1000, stock = 5, likeCount = 0L, brandId = 1L)

            val ex = assertThrows<CoreException> { product.restoreStock(0) }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_BAD_REQUEST)
        }
    }
}
